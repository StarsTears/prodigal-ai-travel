package com.prodigal.travel.rag.geo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 地理层级闭包表：RAG 地理召回的数据底座。
 *
 * <p>数据来自 {@code classpath:/geo/geo-hierarchy.json}（由高德 AMap adcode 表预生成，仅保留省/市两级）。
 * 它解决「海南 ⊃ 三亚」这类<b>层级从属关系</b>——纯向量语义与主题词守卫都认不出这种关系，
 * 但闭包表可以：
 * <ul>
 *     <li>{@link #detectRegions(String)}：从用户查询里识别提到的行政区；</li>
 *     <li>{@link #toGeoFilter(Set)}：拿到该行政区及其全部后代（省→市）的标签集合；</li>
 *     <li>{@link #matchAll(String)}：从文档正文（如「地理位置」行）提取省/市，用于建库打标。</li>
 * </ul>
 */
@Slf4j
@Component
public class GeoClosureTable {

    /** 自治区核心名映射：去掉民族前缀与「自治区」后缀，和用户口语/文档简称对齐 */
    private static final Map<String, String> AUTONOMOUS_CORE = Map.of(
            "广西壮族自治区", "广西",
            "新疆维吾尔自治区", "新疆",
            "内蒙古自治区", "内蒙古",
            "宁夏回族自治区", "宁夏",
            "西藏自治区", "西藏"
    );

    private final Map<String, Region> byAdcode = new HashMap<>();
    private final Map<String, List<String>> children = new HashMap<>();
    private final Map<String, String> fullNameToAdcode = new HashMap<>();
    private final Map<String, String> coreNameToAdcode = new HashMap<>();

    @PostConstruct
    public void load() {
        try (InputStream in = new ClassPathResource("geo/geo-hierarchy.json").getInputStream()) {
            Root root = new ObjectMapper().readValue(in, Root.class);
            if (root.regions == null) {
                log.warn("地理层级数据为空: geo/geo-hierarchy.json");
                return;
            }
            for (Region r : root.regions) {
                byAdcode.put(r.adcode, r);
                fullNameToAdcode.put(r.name, r.adcode);
                coreNameToAdcode.putIfAbsent(coreName(r.name), r.adcode);
                if (r.parent != null && byAdcode.containsKey(r.parent)) {
                    children.computeIfAbsent(r.parent, k -> new ArrayList<>()).add(r.adcode);
                }
            }
            log.info("地理层级闭包表加载完成: 节点数={}", byAdcode.size());
        } catch (Exception e) {
            // 加载失败不能让应用起不来，但地理召回会退化为纯向量，需明确告警
            log.error("地理层级闭包表加载失败，地理召回将退化为纯向量检索", e);
        }
    }

    /** 仅保留省级/市级的简称（去除「省/市/特别行政区」后缀） */
    private static String coreName(String name) {
        if (AUTONOMOUS_CORE.containsKey(name)) {
            return AUTONOMOUS_CORE.get(name);
        }
        return name.replaceAll("(省|市|特别行政区)$", "");
    }

    /**
     * 文档打标：在正文里用<b>全称</b>精确匹配所有出现的行政区（文档用全称，误命中率低）。
     */
    public List<Region> matchAll(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Region> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : fullNameToAdcode.entrySet()) {
            if (text.contains(entry.getKey())) {
                Region r = byAdcode.get(entry.getValue());
                if (r != null) {
                    result.add(r);
                }
            }
        }
        return result;
    }

    /**
     * 查询识别：先从查询里匹配<b>全称</b>，再退而匹配<b>核心名</b>（带后边界守卫，避免「海南」误命中「海南州」）。
     * 不识别国家级（中国），否则会展开成全国。
     */
    public Set<String> detectRegions(String query) {
        Set<String> found = new LinkedHashSet<>();
        if (query == null || query.isBlank()) {
            return found;
        }
        for (Map.Entry<String, String> entry : fullNameToAdcode.entrySet()) {
            if ("country".equals(byAdcode.get(entry.getValue()).level)) {
                continue;
            }
            if (query.contains(entry.getKey())) {
                found.add(entry.getValue());
            }
        }
        for (Map.Entry<String, String> entry : coreNameToAdcode.entrySet()) {
            String core = entry.getKey();
            if (core.length() < 2) {
                continue;
            }
            int from = 0;
            int idx;
            while ((idx = query.indexOf(core, from)) >= 0) {
                int after = idx + core.length();
                // 后边界守卫：核心名后面若紧跟行政区字（州/县/市…），视为更长行政名的一部分，跳过
                if (after >= query.length() || !isAdminChar(query.charAt(after))) {
                    Region r = byAdcode.get(entry.getValue());
                    if (r != null && !"country".equals(r.level)) {
                        found.add(entry.getValue());
                    }
                }
                from = idx + 1;
            }
        }
        return found;
    }

    private static boolean isAdminChar(char c) {
        return c == '省' || c == '市' || c == '县' || c == '区' || c == '州'
                || c == '盟' || c == '旗';
    }

    /**
     * 给定若干被识别的行政区，返回其全部后代（含自身）对应的「省/市」标签集合，供元数据过滤使用。
     */
    public GeoFilter toGeoFilter(Set<String> adcodes) {
        Set<String> provinces = new LinkedHashSet<>();
        Set<String> cities = new LinkedHashSet<>();
        for (String adcode : adcodes) {
            for (String node : subtree(adcode)) {
                Region r = byAdcode.get(node);
                if (r == null) {
                    continue;
                }
                if ("province".equals(r.level)) {
                    provinces.add(r.name);
                } else if ("city".equals(r.level)) {
                    cities.add(r.name);
                }
            }
        }
        return new GeoFilter(provinces, cities);
    }

    /** 闭包展开：返回节点自身 + 所有后代的 adcode */
    private Set<String> subtree(String rootAdcode) {
        Set<String> result = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        if (byAdcode.containsKey(rootAdcode)) {
            queue.add(rootAdcode);
        }
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!result.add(current)) {
                continue;
            }
            for (String child : children.getOrDefault(current, List.of())) {
                queue.add(child);
            }
        }
        return result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Root {
        public List<Region> regions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Region {
        public String name;
        public String adcode;
        public String level;
        public String parent;
    }

    /** 地理过滤条件：命中的省集合 + 市集合（用于 SQL metadata 过滤） */
    public static class GeoFilter {
        public final Set<String> provinces;
        public final Set<String> cities;

        public GeoFilter(Set<String> provinces, Set<String> cities) {
            this.provinces = provinces;
            this.cities = cities;
        }

        public boolean isEmpty() {
            return provinces.isEmpty() && cities.isEmpty();
        }
    }
}
