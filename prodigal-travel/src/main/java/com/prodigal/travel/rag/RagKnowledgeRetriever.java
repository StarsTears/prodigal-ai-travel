package com.prodigal.travel.rag;

import com.prodigal.travel.rag.geo.GeoClosureTable;
import com.prodigal.travel.rag.geo.GeoTagRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RAG 知识库检索器：整个 RAG 链路唯一的数据来源。
 *
 * <p>在原有「查询规整 + 向量召回 + 主题词守卫」基础上，新增<b>地理层级混合召回</b>：
 * <ul>
 *     <li><b>LLM 多查询扩展</b>（{@link TravelMultiQueryExpander}）：把原查询改写成若干语义变体，扩大向量召回面；</li>
 *     <li><b>地理标签确定性召回</b>（{@link GeoTagRepository}）：识别查询里的行政区，按 province/city 标签
 *         直接取数，可靠地解决「搜海南召回不了三亚」这类层级从属盲区（向量语义认不出省⊃市）；</li>
 *     <li>两者召回结果按内容去重合并；被地理标签命中的片段<b>豁免</b>相似度阈值与主题词守卫，
 *         因为地理命中本身就是强相关信号。</li>
 * </ul>
 */
@Slf4j
@Component
public class RagKnowledgeRetriever {

    private static final Pattern CJK_RUN = Pattern.compile("[\\u4e00-\\u9fa5]{2,}");

    /** 常见虚词/通用词的二字组合，命中它们不代表命中了主题 */
    private static final Set<String> STOP_BIGRAMS = new HashSet<>(List.of(
            "什么", "怎么", "怎样", "如何", "哪些", "哪个", "哪里", "多少", "几月", "几天",
            "可以", "需要", "推荐", "介绍", "一下", "请问", "旅游", "攻略", "我们", "我想",
            "知道", "有没有", "地方", "时候", "建议", "关于", "给我", "说说", "想去", "适合",
            "最佳", "路线", "景点", "好玩", "玩的", "有什", "么好", "萨有", "想去", "去的",
            "还有", "以及", "比较", "非常", "帮忙", "麻烦", "告诉", "准备", "注意", "事项",
            "行程", "安排", "出发", "交通", "门票", "价格", "天气", "季节", "附近", "周边",
            "一日", "二日", "三日", "附近", "这些", "那些", "这个", "那个", "是否", "能否",
            "帮我", "请帮", "想问", "问一", "一下", "给我", "来点", "讲讲", "了解", "咨询"
    ));

    private final QueryRewriter queryRewriter;
    private final GeoClosureTable geoClosureTable;
    private final GeoTagRepository geoTagRepository;
    private final TravelMultiQueryExpander multiQueryExpander;

    @Value("${prodigal.rag.similarity-threshold:0.35}")
    private double similarityThreshold = 0.35;

    @Value("${prodigal.rag.top-k:5}")
    private int topK = 5;

    @Value("${prodigal.rag.keyword-guard:true}")
    private boolean keywordGuardEnabled = true;

    /** 是否启用 LLM 多查询扩展（每次 RAG 会产生额外模型调用，成本较高；关闭则只用原查询向量召回） */
    @Value("${prodigal.rag.llm-expansion-enabled:true}")
    private boolean llmExpansionEnabled = true;

    /** 是否启用地理标签确定性召回（推荐常开，是修复「海南→三亚」的可靠主干） */
    @Value("${prodigal.rag.geo-enabled:true}")
    private boolean geoEnabled = true;

    public RagKnowledgeRetriever(QueryRewriter queryRewriter,
                                 GeoClosureTable geoClosureTable,
                                 GeoTagRepository geoTagRepository,
                                 TravelMultiQueryExpander multiQueryExpander) {
        this.queryRewriter = queryRewriter;
        this.geoClosureTable = geoClosureTable;
        this.geoTagRepository = geoTagRepository;
        this.multiQueryExpander = multiQueryExpander;
    }

    /**
     * 检索并组装可直接放进 system 区的知识库证据。
     *
     * @return 证据片段；无有效证据时返回 {@code null}（上层应直接拒答，不要调用生成模型）
     */
    public String retrieve(String question, VectorStore store) {
        if (store == null || !StringUtils.hasText(question)) {
            return null;
        }
        String query = queryRewriter.rewriteForRetrieval(question);

        Set<String> seen = new HashSet<>();
        Set<String> geoHashes = new HashSet<>();
        List<Document> merged = new ArrayList<>();

        // —— 1) LLM 多查询扩展（向量召回面扩大）——
        boolean usedLlm = false;
        if (llmExpansionEnabled && multiQueryExpander != null) {
            try {
                List<Query> expanded = multiQueryExpander.expand(query);
                for (Query q : expanded) {
                    for (Document d : vectorSearch(store, q.text())) {
                        if (seen.add(key(d))) {
                            merged.add(d);
                        }
                    }
                }
                usedLlm = true;
            } catch (Exception e) {
                log.warn("LLM 查询扩展失败，回退为原始查询向量召回, query={}", query, e);
            }
        }
        if (!usedLlm) {
            for (Document d : vectorSearch(store, query)) {
                if (seen.add(key(d))) {
                    merged.add(d);
                }
            }
        }

        // —— 2) 地理标签确定性召回（修复层级从属盲区的主干）——
        if (geoEnabled && geoClosureTable != null && geoTagRepository != null) {
            Set<String> regions = geoClosureTable.detectRegions(query);
            if (!regions.isEmpty()) {
                GeoClosureTable.GeoFilter filter = geoClosureTable.toGeoFilter(regions);
                if (!filter.isEmpty()) {
                    for (Document d : geoTagRepository.findByProvincesOrCities(filter.provinces, filter.cities)) {
                        String k = key(d);
                        if (seen.add(k)) {
                            merged.add(d);
                            geoHashes.add(k);
                        }
                    }
                    log.info("地理标签召回: query={}, 识别地区={}, 省={}, 市={}", query, regions, filter.provinces, filter.cities);
                }
            }
        }

        // —— 3) 阈值过滤 + 主题守卫（地理命中片段豁免）——
        List<Document> candidates = merged.stream()
                .filter(Objects::nonNull)
                .filter(d -> StringUtils.hasText(d.getText()))
                .toList();
        String scores = candidates.stream()
                .map(d -> d.getScore() == null ? "n/a" : String.format(Locale.ROOT, "%.3f", d.getScore()))
                .collect(Collectors.joining(", "));
        List<Document> valid = new ArrayList<>();
        for (Document doc : candidates) {
            boolean geo = geoHashes.contains(key(doc));
            boolean scoreOk = geo || doc.getScore() == null || doc.getScore() >= similarityThreshold;
            if (!scoreOk) {
                continue;
            }
            if (!geo && keywordGuardEnabled && !topicMatches(question, doc.getText())) {
                continue;
            }
            valid.add(doc);
        }
        log.info("RAG 检索: query={}, 合并候选={}, scores=[{}], 阈值过滤后={}, threshold={}, topK={}, geo命中={}",
                query, candidates.size(), scores, valid.size(), similarityThreshold, topK, geoHashes.size());
        if (valid.isEmpty()) {
            log.warn("RAG 无有效证据: query={}, 召回分数=[{}], threshold={}。"
                            + "若向量召回为 0 检查 pgvector 连接；地理召回为空说明文档未带 province/city 标签，"
                            + "需清空 travel_vector_store 表后重启重建。",
                    query, scores, similarityThreshold);
            return null;
        }

        StringBuilder sb = new StringBuilder("\n\n## 知识库检索结果（唯一事实来源）\n");
        for (int i = 0; i < valid.size(); i++) {
            Document doc = valid.get(i);
            Map<String, Object> metadata = doc.getMetadata();
            Object source = metadata == null ? null : metadata.get("filename");
            boolean geo = geoHashes.contains(key(doc));
            sb.append("\n---\n[资料").append(i + 1)
                    .append(" | 来源: ").append(source == null ? "知识库" : source)
                    .append(geo ? " | 地理命中" : "").append("]\n")
                    .append(doc.getText().strip());
        }
        sb.append("\n\n[以上为全部可用资料，不得使用任何其它来源的信息]");
        return sb.toString();
    }

    private List<Document> vectorSearch(VectorStore store, String q) {
        try {
            // 相似度阈值不下推 SQL：SQL 只取距离最近的 topK 条，阈值判断放到 Java 层
            return store.similaritySearch(SearchRequest.builder()
                    .query(q)
                    .topK(topK)
                    .similarityThresholdAll()
                    .build());
        } catch (Exception e) {
            log.error("RAG 向量检索失败, query={}", q, e);
            return List.of();
        }
    }

    /** 去重/地理命中判定的内容键 */
    private static String key(Document d) {
        String t = d == null ? "" : d.getText();
        return t == null ? "" : t.strip();
    }

    /**
     * 主题词校验：把问题里的中文连续串切成二字组合，去掉虚词后至少有一个要出现在证据里。
     * 纯英文/符号问题不做拦截，交由严格提示词判断。
     */
    static boolean topicMatches(String question, String evidence) {
        Set<String> grams = new LinkedHashSet<>();
        Matcher matcher = CJK_RUN.matcher(question);
        while (matcher.find()) {
            String run = matcher.group();
            for (int i = 0; i + 2 <= run.length(); i++) {
                String gram = run.substring(i, i + 2);
                if (!STOP_BIGRAMS.contains(gram)) {
                    grams.add(gram);
                }
            }
        }
        if (grams.isEmpty()) {
            return true;
        }
        for (String gram : grams) {
            if (evidence.contains(gram)) {
                return true;
            }
        }
        return false;
    }

    /** 供测试与运维查看当前生效参数 */
    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public int getTopK() {
        return topK;
    }

    public boolean isKeywordGuardEnabled() {
        return keywordGuardEnabled;
    }

    public boolean isLlmExpansionEnabled() {
        return llmExpansionEnabled;
    }

    public boolean isGeoEnabled() {
        return geoEnabled;
    }

    /** 返回问题中被用于主题校验的二字词，便于日志排查 */
    public static List<String> debugTopicGrams(String question) {
        List<String> grams = new ArrayList<>();
        Matcher matcher = CJK_RUN.matcher(question);
        while (matcher.find()) {
            String run = matcher.group();
            for (int i = 0; i + 2 <= run.length(); i++) {
                String gram = run.substring(i, i + 2);
                if (!STOP_BIGRAMS.contains(gram)) {
                    grams.add(gram);
                }
            }
        }
        return grams;
    }
}
