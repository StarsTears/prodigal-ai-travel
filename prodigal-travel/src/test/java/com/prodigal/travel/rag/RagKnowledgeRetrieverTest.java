package com.prodigal.travel.rag;

import com.prodigal.travel.constants.TravelConstant;
import com.prodigal.travel.rag.geo.GeoClosureTable;
import com.prodigal.travel.rag.geo.GeoTagRepository;
import com.prodigal.travel.rag.TravelMultiQueryExpander;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RAG 严格检索的纯逻辑回归测试：不连接 pgvector，也不调用任何大模型。
 */
class RagKnowledgeRetrieverTest {

    private static final String HUANGSHAN = "# 黄山旅游指南\n\n## 最佳季节\n黄山春季与秋季气候舒适，云海出现概率较高。";

    private final RagKnowledgeRetriever retriever =
            new RagKnowledgeRetriever(new QueryRewriter(), null, null, null);

    /** 通过真实 Spring 容器创建 Bean，验证 @Value 占位符确实被解析并覆盖字段默认值 */
    private static RagKnowledgeRetriever springRetriever(MockEnvironment environment) {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.setEnvironment(environment);
            ctx.getBeanFactory().registerSingleton("queryRewriter", new QueryRewriter());
            // 新增的地理/LLM 依赖在单元测试里用空实现占位：地理表未加载→识别为空；
            // 标签召回与 LLM 扩展返回空，不干扰「向量召回 + 阈值 + 主题守卫」的回归断言。
            ctx.getBeanFactory().registerSingleton("geoClosureTable", new GeoClosureTable());
            GeoTagRepository geoTagRepository = mock(GeoTagRepository.class);
            when(geoTagRepository.findByProvincesOrCities(any(), any())).thenReturn(List.of());
            ctx.getBeanFactory().registerSingleton("geoTagRepository", geoTagRepository);
            TravelMultiQueryExpander expander = mock(TravelMultiQueryExpander.class);
            when(expander.expand(any(String.class))).thenAnswer(inv -> List.of(new Query((String) inv.getArgument(0))));
            ctx.getBeanFactory().registerSingleton("multiQueryExpander", expander);
            ctx.registerBean(RagKnowledgeRetriever.class);
            ctx.refresh();
            return ctx.getBean(RagKnowledgeRetriever.class);
        }
    }

    @Test
    void rewriteIsPureLocalAndKeepsEntity() {
        String query = new QueryRewriter().rewriteForRetrieval("请问，帮我介绍一下拉萨？");
        assertFalse(query.contains("请问"), "口语前缀应被剥离");
        assertFalse(query.contains("帮我"));
        assertTrue(query.contains("拉萨"), "实体词必须保留");
    }

    @Test
    void returnsEvidenceWithSourceForRelevantQuestion() {
        VectorStore store = storeReturning(doc("huangshan.md", HUANGSHAN, null));
        String evidence = retriever.retrieve("黄山最佳季节是什么时候", store);
        assertNotNull(evidence);
        assertTrue(evidence.contains("黄山春季与秋季"));
        assertTrue(evidence.contains("huangshan.md"), "证据应携带来源文件名");
        assertTrue(evidence.contains("唯一事实来源"));
    }

    @Test
    void refusesOutOfScopeQuestionEvenWhenVectorStoreReturnsSimilarChunk() {
        // 知识库里没有拉萨，但向量检索一定会返回「最相似」的其它目的地片段
        VectorStore store = storeReturning(doc("huangshan.md", HUANGSHAN, null));
        assertNull(retriever.retrieve("拉萨有什么好玩的", store),
                "跑题片段不能当作证据交给模型");
    }

    @Test
    void multipleChunksAreCombinedIntoOneEvidenceForSynthesis() {
        // 多篇（多分块）证据应一起交给模型，由模型去重、归纳后输出，而不是只取第一条
        String season = "# 黄山旅游指南\n\n## 最佳季节\n黄山春季与秋季气候舒适，云海出现概率较高。";
        String ticket = "# 黄山旅游指南\n\n## 门票\n黄山旺季门票 190 元，淡季 150 元。";
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc("huangshan.md", season, 0.82),
                        doc("huangshan.md", ticket, 0.76)));
        String evidence = retriever.retrieve("黄山最佳季节和门票价格", store);
        assertNotNull(evidence);
        assertTrue(evidence.contains("云海出现概率较高"), "第一篇资料必须进入上下文");
        assertTrue(evidence.contains("旺季门票 190 元"), "第二篇资料必须进入上下文");
        assertTrue(evidence.contains("[资料1") && evidence.contains("[资料2"),
                "多资料应分别编号，便于模型归纳与标注来源");
        assertTrue(evidence.contains("不得使用任何其它来源的信息"));
    }

    @Test
    void strictPromptCarriesCanonicalRefusalAndSynthesisRule() {
        // 部分命中时模型也要用与短路分支完全相同的拒答语，不能自行编一句
        assertTrue(TravelConstant.RAG_STRICT_SYSTEM_PROMPT.contains(
                TravelConstant.RAG_NO_RESULT_ANSWER.strip()));
        assertTrue(TravelConstant.RAG_STRICT_SYSTEM_PROMPT.contains("归纳"));
        assertTrue(TravelConstant.RAG_STRICT_SYSTEM_PROMPT.contains("整合不等于补充"));
    }

    @Test
    void returnsNullWhenNoDocumentsRecalled() {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        assertNull(retriever.retrieve("黄山门票", store));
    }

    @Test
    void returnsNullWhenRetrievalThrows() {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new IllegalStateException("pgvector 不可用"));
        assertNull(retriever.retrieve("黄山门票", store),
                "检索失败不能退化成模型自由发挥");
    }

    @Test
    void filtersDocumentsBelowSimilarityThreshold() {
        VectorStore store = storeReturning(doc("huangshan.md", HUANGSHAN, 0.20));
        assertNull(retriever.retrieve("黄山门票", store));
    }

    @Test
    void strictThresholdIsNotPushedDownToSql() {
        // 回归：0.65 这类绝对阈值下推到 SQL 会变成 `embedding <=> ? < 0.35`，
        // 短问题检索长文档分块时 pgvector 直接返回 0 行（线上表现为「召回=0」）。
        VectorStore store = storeReturning(doc("huangshan.md", HUANGSHAN, 0.42));
        retriever.retrieve("泰山", store);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(store).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();
        assertEquals(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL,
                request.getSimilarityThreshold(), 0.0001,
                "SQL 层必须宽召回，严格阈值只留在应用层");
        assertEquals(5, request.getTopK());
        assertTrue(request.getQuery().contains("泰山"));
    }

    @Test
    void applicationLayerKeepsOnlyNeighboursAboveThreshold() {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                doc("huangshan.md", "黄山春季与秋季气候舒适。", 0.42),
                doc("huangshan.md", "黄山旺季门票 190 元。", 0.30),
                doc("huangshan.md", "恒山索道运营时间说明。", 0.10)));
        String evidence = retriever.retrieve("黄山最佳季节", store);
        assertNotNull(evidence);
        assertTrue(evidence.contains("春季与秋季"), "高于阈值的分块应进入证据");
        assertFalse(evidence.contains("190 元"), "低于 0.35 的邻居不能进入证据");
        assertFalse(evidence.contains("索道"), "最低分分块不能进入证据");
        assertTrue(evidence.contains("[资料1") && !evidence.contains("[资料2"),
                "证据编号应连续且只包含通过过滤的分块");
    }

    @Test
    void keywordGuardCanBeDisabledByConfig() {
        RagKnowledgeRetriever relaxed = springRetriever(
                new MockEnvironment().withProperty("prodigal.rag.keyword-guard", "false"));
        VectorStore store = storeReturning(doc("huangshan.md", HUANGSHAN, null));
        assertFalse(relaxed.isKeywordGuardEnabled());
        assertNotNull(relaxed.retrieve("拉萨有什么好玩的", store));
    }

    @Test
    void thresholdAndTopKAreConfigurable() {
        RagKnowledgeRetriever tuned = springRetriever(new MockEnvironment()
                .withProperty("prodigal.rag.similarity-threshold", "0.8")
                .withProperty("prodigal.rag.top-k", "3"));
        assertTrue(Math.abs(tuned.getSimilarityThreshold() - 0.8) < 0.0001,
                "@Value 应把 0.8 注入 similarityThreshold，而不是保留默认 0.35");
        assertEquals(3, tuned.getTopK());
    }

    @Test
    void valuePlaceholdersFallBackToDefaultsWhenPropertiesMissing() {
        // 无任何配置时，@Value 的 :default 段与字段初始化值都要保证参数可用
        RagKnowledgeRetriever fromSpring = springRetriever(new MockEnvironment());
        assertTrue(Math.abs(fromSpring.getSimilarityThreshold() - 0.35) < 0.0001);
        assertEquals(5, fromSpring.getTopK());
        assertTrue(fromSpring.isKeywordGuardEnabled());

        RagKnowledgeRetriever manual = new RagKnowledgeRetriever(new QueryRewriter(), null, null, null);
        assertTrue(Math.abs(manual.getSimilarityThreshold() - 0.35) < 0.0001,
                "脱离容器手动构造时也不能退化成 0.0（否则阈值过滤形同虚设）");
        assertEquals(5, manual.getTopK());
        assertTrue(manual.isKeywordGuardEnabled());
    }

    private static Document doc(String filename, String text, Double score) {
        return Document.builder()
                .text(text)
                .metadata(Map.of("filename", filename))
                .score(score)
                .build();
    }

    private static VectorStore storeReturning(Document document) {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(document));
        return store;
    }
}
