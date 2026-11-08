package com.prodigal.travel.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
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
 * <p>与旧实现的区别：
 * <ul>
 *     <li>查询改写改为纯本地字符串规整，不再调用大模型；</li>
 *     <li>相似度阈值不再下推到 SQL（避免 pgvector 零召回），改为宽召回 topK 后在应用层按 score 过滤，并打印分数分布；</li>
 *     <li>增加主题词校验，避免「问拉萨、召回黄山」这类明显跑题的片段被送给模型；</li>
 *     <li>任何一步无有效证据都返回 {@code null}，由上层直接短路拒答，绝不退化成模型自由发挥；</li>
 *     <li>检索片段自带来源文件名，便于答案标注出处。</li>
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

    /**
     * 配置统一用 {@code @Value} 声明式注入，与项目其它配置类（如 ToolRegisterConfig）保持一致。
     * 占位符自带默认值：即使 application.yml 未声明该段也能启动；字段初始化值则是脱离
     * Spring 容器手动 new（如单元测试）时的兜底，避免退化成 0 / 0.0 / false。
     */
    /**
     * 相似度下限（0~1，越大越严格）。仅在 Java 层用于筛选 pgvector 返回的最近邻，
     * 不再下推到 SQL。默认 0.35 是针对「短问题 → 长文档分块」跨文体检索的经验值：
     * 实测同一篇文档的两个分块之间相似度也才约 0.67，用 0.65 做门槛会让 pgvector
     * 一条都召回不了（日志表现为「召回=0」）。请依据日志里的 scores 分布校准。
     */
    @Value("${prodigal.rag.similarity-threshold:0.35}")
    private double similarityThreshold = 0.35;

    @Value("${prodigal.rag.top-k:5}")
    private int topK = 5;

    @Value("${prodigal.rag.keyword-guard:true}")
    private boolean keywordGuardEnabled = true;

    public RagKnowledgeRetriever(QueryRewriter queryRewriter) {
        this.queryRewriter = queryRewriter;
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
        List<Document> documents;
        try {
            // 关键：绝对相似度阈值不能下推到 SQL。
            // Spring AI 的 PgVectorStore 会把它翻译成 `embedding <=> ? < 1 - threshold`，
            // 例如 0.65 → distance < 0.35。「泰山」这类短问题与长文档分块是跨文体检索，
            // 余弦相似度天然偏低，这个 WHERE 会把候选全部挡掉，similaritySearch 直接返回
            // 0 行（日志「召回=0」），此时 Java 层的过滤与主题词守卫都无从执行。
            // 改为 similarityThresholdAll()：SQL 只取距离最近的 topK 条，阈值判断放到 Java 层，
            // 既能打印真实分数便于调参，也不会因为一个拍脑袋的阈值一票否决。
            documents = store.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThresholdAll()
                    .build());
        } catch (Exception e) {
            // 检索失败绝不能降级为「让模型自己回答」
            log.error("RAG 知识库检索失败, query={}", query, e);
            return null;
        }
        List<Document> candidates = documents == null ? List.of() : documents;
        // PgVectorStore 返回的 Document.score 已经是 1 - 余弦距离（相似度），不是距离本身
        String scores = candidates.stream()
                .filter(Objects::nonNull)
                .map(Document::getScore)
                .map(score -> score == null ? "n/a" : String.format(Locale.ROOT, "%.3f", score))
                .collect(Collectors.joining(", "));
        List<Document> valid = candidates.stream()
                .filter(doc -> doc != null && StringUtils.hasText(doc.getText()))
                .filter(doc -> doc.getScore() == null || doc.getScore() >= similarityThreshold)
                .toList();
        log.info("RAG 检索: query={}, 召回={}, scores=[{}], 阈值过滤后={}, threshold={}, topK={}",
                query, candidates.size(), scores, valid.size(), similarityThreshold, topK);
        if (valid.isEmpty()) {
            log.warn("RAG 无有效证据: query={}, 召回分数=[{}], threshold={}。"
                            + "若召回为 0，检查 pgvector 连接与表数据；若分数普遍偏低且彼此接近，"
                            + "多半是库内向量由其它 embedding 模型/其它批次灌入，需清空表后用当前模型重建知识库。",
                    query, scores, similarityThreshold);
            return null;
        }

        String evidenceText = valid.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));
        if (keywordGuardEnabled && !topicMatches(question, evidenceText)) {
            log.info("RAG 检索结果与问题主题不匹配，判定知识库无相关资料: query={}", query);
            return null;
        }

        StringBuilder sb = new StringBuilder("\n\n## 知识库检索结果（唯一事实来源）\n");
        for (int i = 0; i < valid.size(); i++) {
            Document doc = valid.get(i);
            Map<String, Object> metadata = doc.getMetadata();
            Object source = metadata == null ? null : metadata.get("filename");
            sb.append("\n---\n[资料").append(i + 1)
                    .append(" | 来源: ").append(source == null ? "知识库" : source).append("]\n")
                    .append(doc.getText().strip());
        }
        sb.append("\n\n[以上为全部可用资料，不得使用任何其它来源的信息]");
        return sb.toString();
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
