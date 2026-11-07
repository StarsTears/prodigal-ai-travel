package com.prodigal.travel.rag;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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


    @Bean(name = "pgVectorDataSourceProperties")
    @ConfigurationProperties(prefix = "pgvector.datasource")
    public DataSourceProperties pgVectorDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "pgVectorDataSource")
    public DataSource pgVectorDataSource(
            @Qualifier("pgVectorDataSourceProperties") DataSourceProperties properties) {
        DataSource ds = properties.initializeDataSourceBuilder().build();
        return ds;
    }

    // 基于 PG DataSource 创建 JdbcTemplate
    @Bean(name = "pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate(
            @Qualifier("pgVectorDataSource") DataSource dataSource) {
        // 此时绑定已完成
        if (dataSource instanceof HikariDataSource hikari) {
            log.info("PgVector JDBC URL: " + hikari.getJdbcUrl());
        }
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public VectorStore pgVectorStore(  @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel){
        PgVectorStore pgVectorStore = PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1536)                    // Optional: defaults to model dimensions or 1536
                .distanceType(COSINE_DISTANCE)       // Optional: defaults to COSINE_DISTANCE
                .indexType(HNSW)                     // Optional: defaults to HNSW
                .initializeSchema(true)              // Optional: defaults to false
                .schemaName("public")                // Optional: defaults to "public"
                .vectorTableName("travel_vector_store")     // Optional: defaults to "vector_store"
                .maxDocumentBatchSize(10000)
                .build();
        //加载文档
        List<Document> documents = travelKnowledgeLoader.loadMarkdown();
        //切分文档
        List<Document> splitDocuments = travelTokenTextSplitter.splitCustomized(documents);
        //先按正文去重，只保留新增分块，避免对已存在内容重复调用 LLM
        List<Document> newDocuments = filterNewDocuments(splitDocuments, jdbcTemplate);

        if (newDocuments.isEmpty()) {
            log.info("No new knowledge chunks found, skip loading to pgVectorStore.");
            return pgVectorStore;
        }
        //仅对新增分块做关键词增强，节省 token 与启动时间
        List<Document> enrichDocuments = travelKeywordEnricher.enrichDocument(newDocuments);
        for (int i = 0; i < enrichDocuments.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, enrichDocuments.size());
            pgVectorStore.add(enrichDocuments.subList(i, end));
            log.info("Knowledge chunks loaded to pgVectorStore: {}/{}", enrichDocuments.size(), splitDocuments.size());
        }
        return pgVectorStore;
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
