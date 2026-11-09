package com.prodigal.travel.rag.geo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按地理标签确定性召回：直接读 pgvector 表的 {@code metadata} JSONB，按 province/city 标签过滤。
 *
 * <p>为什么不用向量相似度？因为「海南」与「三亚亚龙湾」分块的余弦相似度天然偏低，会掉到阈值以下，
 * 纯向量召不回。而标签是建库时写死的确定事实，按标签取数 100% 命中，
 * 正好弥补向量语义在「层级从属」上的盲区。这也是「地理层级标签」这一半混合方案的核心。
 */
@Repository
public class GeoTagRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeoTagRepository(@org.springframework.beans.factory.annotation.Qualifier("pgVectorJdbcTemplate")
                            JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 命中任一省或任一市的文档（OR 关系）。传入空集合直接返回空，避免全表扫描。
     */
    public List<Document> findByProvincesOrCities(Set<String> provinces, Set<String> cities) {
        if ((provinces == null || provinces.isEmpty()) && (cities == null || cities.isEmpty())) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT content, metadata FROM public.travel_vector_store WHERE ");
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        if (provinces != null && !provinces.isEmpty()) {
            conditions.add("metadata->>'province' IN (" + placeholders(provinces.size()) + ")");
            params.addAll(provinces);
        }
        if (cities != null && !cities.isEmpty()) {
            conditions.add("metadata->>'city' IN (" + placeholders(cities.size()) + ")");
            params.addAll(cities);
        }
        sql.append(String.join(" OR ", conditions));

        try {
            return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
                String content = rs.getString("content");
                String meta = rs.getString("metadata");
                Map<String, Object> metadata = new java.util.LinkedHashMap<>();
                if (meta != null && !meta.isBlank()) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> parsed = objectMapper.readValue(meta, Map.class);
                        if (parsed != null) {
                            metadata.putAll(parsed);
                        }
                    } catch (Exception ignored) {
                        // 元数据解析失败不影响召回，保留空 metadata
                    }
                }
                return new Document(content == null ? "" : content, metadata);
            }, params.toArray());
        } catch (Exception e) {
            // 召回失败绝不能降级为「让模型自己回答」，返回空让上层走拒答
            org.slf4j.LoggerFactory.getLogger(GeoTagRepository.class)
                    .error("地理标签召回失败, sql={}", sql, e);
            return List.of();
        }
    }

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(i == 0 ? "?" : ",?");
        }
        return sb.toString();
    }
}
