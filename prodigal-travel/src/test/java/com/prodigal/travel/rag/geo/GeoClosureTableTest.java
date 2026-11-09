package com.prodigal.travel.rag.geo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证地理闭包表的核心能力：查询识别、后代展开、文档打标、边界守卫。
 */
class GeoClosureTableTest {

    private final GeoClosureTable table = new GeoClosureTable();

    @BeforeEach
    void setUp() {
        // 单测中无 Spring 容器，手动触发 @PostConstruct 逻辑
        table.load();
    }

    @Test
    void detectProvinceFromQuery() {
        Set<String> regions = table.detectRegions("海南有什么好玩的");
        assertTrue(regions.contains("460000"), "应识别到海南省: " + regions);
    }

    @Test
    void detectCityFromQuery() {
        Set<String> regions = table.detectRegions("我想去三亚玩");
        assertTrue(regions.contains("460200"), "应识别到三亚市: " + regions);
    }

    @Test
    void geoFilterIncludesDescendantCities() {
        GeoClosureTable.GeoFilter filter = table.toGeoFilter(Set.of("460000"));
        assertTrue(filter.provinces.contains("海南省"), "应包含省份: " + filter.provinces);
        assertTrue(filter.cities.contains("三亚市"), "应包含后代城市三亚市: " + filter.cities);
        assertTrue(filter.cities.contains("海口市"));
    }

    @Test
    void matchAllFromDocumentText() {
        List<GeoClosureTable.Region> regions =
                table.matchAll("地理位置：海南省三亚市亚龙湾国家旅游度假区");
        assertTrue(regions.stream().anyMatch(r -> "海南省".equals(r.name)), "应提取省份");
        assertTrue(regions.stream().anyMatch(r -> "三亚市".equals(r.name)), "应提取城市");
    }

    @Test
    void shouldNotMisMatchHainanZhou() {
        // 「海南州」(青海的海南藏族自治州) 不应误命中「海南省」
        Set<String> regions = table.detectRegions("海南州天气预报");
        assertFalse(regions.contains("460000"), "海南州不应误命中海南省: " + regions);
    }
}
