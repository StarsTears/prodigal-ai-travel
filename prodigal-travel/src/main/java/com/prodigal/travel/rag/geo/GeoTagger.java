package com.prodigal.travel.rag.geo;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档地理打标器：从文档正文（主要是「地理位置」行）提取省/市，写入元数据。
 *
 * <p>打在<b>源文档</b>上，分块（semantic chunk）会继承这些元数据，
 * 因此重建向量库后每个分块都带 {@code province}/{@code city} 标签，
 * 检索时可用它做确定性的地理召回（弥补向量语义认不出「海南 ⊃ 三亚」的缺陷）。
 */
@Component
public class GeoTagger {

    private final GeoClosureTable geoClosureTable;

    public GeoTagger(GeoClosureTable geoClosureTable) {
        this.geoClosureTable = geoClosureTable;
    }

    /**
     * 对一批源文档打地理标签，返回打标后的新实例（不修改入参）。
     */
    public List<Document> tag(List<Document> sources) {
        if (sources == null || sources.isEmpty()) {
            return sources;
        }
        List<Document> result = new ArrayList<>(sources.size());
        for (Document source : sources) {
            result.add(tagOne(source));
        }
        return result;
    }

    private Document tagOne(Document source) {
        Map<String, Object> metadata = source.getMetadata() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(source.getMetadata());
        List<GeoClosureTable.Region> regions = geoClosureTable.matchAll(source.getText());
        String province = null;
        String city = null;
        for (GeoClosureTable.Region region : regions) {
            if ("province".equals(region.level)) {
                province = region.name;
            } else if ("city".equals(region.level)) {
                city = region.name;
            }
        }
        if (province != null) {
            metadata.put("province", province);
        }
        if (city != null) {
            metadata.put("city", city);
        }
        return new Document(source.getId(), source.getText(), metadata);
    }
}
