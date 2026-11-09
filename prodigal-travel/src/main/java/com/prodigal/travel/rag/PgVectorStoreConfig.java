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
        // 地理标签自愈：若表中已存在数据缺少 province 标签（旧版），或检测异常（如 metadata 列类型异常），
        // 一律清空后强制重建，确保每条分块都带 province/city 标签（地理确定性召回的前提）。
        boolean forceRebuild = false;
        try {
            Integer stale = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.travel_vector_store WHERE metadata->>'province' IS NULL",
                    Integer.class);
            if (stale != null && stale > 0) {
                log.warn("检测到旧版无地理标签数据 {} 条，将清空并重建（带 province/city 标签）", stale);
                forceRebuild = true;
            }
        } catch (Exception e) {
            // 检测失败（如 metadata 列类型不支持 ->> 操作符）也视为需要重建，避免旧无标签数据长期残留
            log.error("地理标签升级检测异常，将强制重建知识库: {}", e.getMessage());
            forceRebuild = true;
        }
        if (forceRebuild) {
            try {
                jdbcTemplate.update("DELETE FROM public.travel_vector_store");
            } catch (Exception e) {
                log.error("清空旧知识库失败，仍尝试覆盖重建: {}", e.getMessage());
            }
        }

        // 加载并打地理标签
        List<Document> documents = travelKnowledgeLoader.loadMarkdown();
        long tagged = documents.stream()
                .filter(d -> d.getMetadata() != null && d.getMetadata().get("province") != null)
                .count();
        log.info("知识库加载源文档 {} 篇，其中带 province 地理标签 {} 篇", documents.size(), tagged);
        if (tagged == 0) {
            log.error("⚠️ 地理打标全部失败！请检查 geo-hierarchy.json 与文档「地理位置」字段，否则地理召回将失效");
        }

        // 切分文档
        List<Document> splitDocuments = travelTokenTextSplitter.splitCustomized(documents);

        // 强制重建时忽略正文去重直接全量入库；否则只入库新增分块（节省 token）
        List<Document> toAdd = forceRebuild ? withContentHash(splitDocuments)
                : filterNewDocuments(splitDocuments, jdbcTemplate);

        if (toAdd.isEmpty()) {
            log.info("No new knowledge chunks found, skip loading to pgVectorStore.");
            return pgVectorStore;
        }
        // 仅对入库分块做关键词增强，节省 token 与启动时间
        List<Document> enrichDocuments = travelKeywordEnricher.enrichDocument(toAdd);
        for (int i = 0; i < enrichDocuments.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, enrichDocuments.size());
            pgVectorStore.add(enrichDocuments.subList(i, end));
            log.info("Knowledge chunks loaded to pgVectorStore: {}/{}", enrichDocuments.size(), splitDocuments.size());
        }
        log.info("知识库入库完成：本次新增 {} 条（均带 province/city 地理标签）", enrichDocuments.size());
        return pgVectorStore;
    }

    /** 强制重建分支用：全量入库，并为每个分块补 contentHash（便于日后增量去重） */
    private List<Document> withContentHash(List<Document> source) {
        List<Document> result = new ArrayList<>(source.size());
        for (Document document : source) {
            if (document == null || document.getText() == null || document.getText().isBlank()) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
            metadata.put("contentHash", sha256Base64(document.getText().trim()));
            result.add(new Document(document.getText().trim(), metadata));
        }
        return result;
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
