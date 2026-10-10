package com.prodigal.travel.rag;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description PgVectorStore - 阿里云 serverless 数据库
 * @since 2026/4/1
 */
@Slf4j
@Configuration
public class PgVectorStoreConfig {
    @Resource
    private TravelKnowledgeLoader travelKnowledgeLoader;
    @Resource
    private TravelTokenTextSplitter travelTokenTextSplitter;
    @Resource
    private TravelKeywordEnricher travelKeywordEnricher;

    private static final int BATCH_SIZE = 25;

    @Bean("pgvectorJdbcTemplate")
    public JdbcTemplate pgvectorJdbcTemplate(DataSource dataSource) {
        if (!(dataSource instanceof DynamicRoutingDataSource dynamicRoutingDataSource)) {
            throw new IllegalStateException("当前 dataSource 不是 DynamicRoutingDataSource，无法获取 pgvector 数据源");
        }
        DataSource pgvectorDataSource = dynamicRoutingDataSource.getDataSource("pgvector");
        if (pgvectorDataSource == null) {
            throw new IllegalStateException("未找到名为 pgvector 的数据源，请检查 spring.datasource.dynamic.datasource.pgvector 配置");
        }
        return new JdbcTemplate(pgvectorDataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public VectorStore pgVectorStore(@Qualifier("pgvectorJdbcTemplate") JdbcTemplate pgvectorJdbcTemplate,
                                     EmbeddingModel dashscopeEmbeddingModel){
        log.info("初始化 PgVectorStore，使用 pgvector 数据源");
        // 打印数据源信息用于验证
        try {
            String url = pgvectorJdbcTemplate.getDataSource().getConnection().getMetaData().getURL();
            log.info("PgVector 实际连接的数据库: {}", url);
        } catch (Exception e) {
            log.error("获取数据源信息失败", e);
        }
        PgVectorStore pgVectorStore = PgVectorStore.builder(pgvectorJdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1536)                    // Optional: defaults to model dimensions or 1536
                .distanceType(COSINE_DISTANCE)       // Optional: defaults to COSINE_DISTANCE
                .indexType(HNSW)                     // Optional: defaults to HNSW
                .initializeSchema(true)              // Optional: defaults to false
                .schemaName("public")                // Optional: defaults to "public"
                .vectorTableName("travel_vector_store")     // Optional: defaults to "vector_store"
                .maxDocumentBatchSize(10000)
                .build();
        return pgVectorStore;
    }

    @Bean
    public ApplicationRunner ragKnowledgeBaseAsyncLoader(@Qualifier("pgVectorStore") VectorStore pgVectorStore,
                                                          @Qualifier("pgvectorJdbcTemplate") JdbcTemplate pgvectorJdbcTemplate) {
        return args -> {
            log.info("应用启动完成，提交 RAG 知识库异步加载任务");
            CompletableFuture.runAsync(() -> {
                long start = System.currentTimeMillis();
                String url = null;
                try {
                    url = pgvectorJdbcTemplate.getDataSource().getConnection().getMetaData().getURL();
                    log.info("PgVector RAG 知识库-实际连接的数据库: {}", url);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }

                if (!(pgVectorStore instanceof PgVectorStore store)) {
                    log.warn("当前 VectorStore 不是 PgVectorStore，跳过 RAG 知识库加载。实际类型: {}",
                            pgVectorStore.getClass().getName());
                    return;
                }
                int inserted = loadDocumentsToVectorStore(store, pgvectorJdbcTemplate);
                log.info("RAG 知识库异步加载完成，新增 {} 条，耗时: {} ms", inserted, System.currentTimeMillis() - start);
            }).exceptionally(ex -> {
                log.error("RAG 知识库异步加载失败", ex);
                return null;
            });
            log.info("RAG 知识库异步加载任务已提交");
        };
    }

    /**
     * 加载文档到向量存储（抽取公共逻辑）
     */
    private int loadDocumentsToVectorStore(PgVectorStore pgVectorStore, JdbcTemplate jdbcTemplate) {
        try {
            long totalStart = System.currentTimeMillis();
            long stageStart = System.currentTimeMillis();
            // 加载原始文档
            List<Document> documents = travelKnowledgeLoader.loadMarkdown();
            log.info("RAG 加载阶段-读取文档: {} 条，耗时 {} ms", documents.size(), System.currentTimeMillis() - stageStart);

            // 切分文档
            stageStart = System.currentTimeMillis();
            List<Document> splitDocuments = travelTokenTextSplitter.splitCustomized(documents);
            log.info("RAG 加载阶段-文档切分: {} 条，耗时 {} ms", splitDocuments.size(), System.currentTimeMillis() - stageStart);

            // 添加关键词
//            List<Document> enrichDocument = travelKeywordEnricher.enrichDocument(splitDocuments);
//            log.info("RAG 加载阶段-关键词增强: {} 条，耗时 {} ms", enrichDocument.size(), System.currentTimeMillis() - stageStart);

            // 过滤已存在的文档
            List<Document> documentsToInsert = filterNewDocuments(splitDocuments,jdbcTemplate);
            log.info("RAG 加载阶段-增量过滤: 待入库 {} 条，耗时 {} ms", documentsToInsert.size(), System.currentTimeMillis() - stageStart);

            if (documentsToInsert.isEmpty()) {
                log.info("没有新的知识块需要加载到 pgVectorStore");
                return 0;
            }

            // 批量插入
            stageStart = System.currentTimeMillis();
            for (int i = 0; i < documentsToInsert.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, documentsToInsert.size());
                pgVectorStore.add(documentsToInsert.subList(i, end));
                log.info("已加载 {}/{} 个知识块", end, documentsToInsert.size());
            }
            log.info("RAG 加载阶段-向量入库: {} 条，耗时 {} ms", documentsToInsert.size(), System.currentTimeMillis() - stageStart);

            log.info("知识块加载完成，新增 {}/{} 条，总耗时 {} ms", documentsToInsert.size(), splitDocuments.size(),
                    System.currentTimeMillis() - totalStart);
            return documentsToInsert.size();

        } catch (Exception e) {
            log.error("加载文档到向量存储失败", e);
            throw new RuntimeException("向量存储初始化失败", e);
        }
    }

    private List<Document> filterNewDocuments(List<Document> source, JdbcTemplate jdbcTemplate) {
        Set<String> existingContents = loadExistingContents(jdbcTemplate);
        List<Document> result = new ArrayList<>(source.size());
        for (Document document : source) {
            if (document == null || document.getText() == null || document.getText().isBlank()) {
                continue;
            }
            String normalizedText = document.getText().trim();
            if (existingContents.contains(normalizedText)) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
            metadata.put("contentHash", sha256Base64(normalizedText));
            result.add(new Document(normalizedText, metadata));
            existingContents.add(normalizedText);
        }
        return result;
    }

    private Set<String> loadExistingContents(JdbcTemplate jdbcTemplate) {
        String sql = "SELECT content FROM public.travel_vector_store WHERE content IS NOT NULL";
        List<String> rows = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1));
        return rows.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    private String sha256Base64(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
