package com.prodigal.travel.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 联网搜索
 * @since 2026/4/2
 */
public class WebSearchTool {
    // SearchAPI 的搜索接口地址
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";
    private static final int MAX_RESULTS = 5;
    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            JSONObject jsonObject = JSONUtil.parseObj(response);

            JSONArray organicResults = jsonObject.getJSONArray("organic_results");
            if (organicResults == null) {
                organicResults = jsonObject.getJSONArray("people_also_search_for");
            }

            if (organicResults == null || organicResults.isEmpty()) {
                JSONObject knowledgeGraph = jsonObject.getJSONObject("knowledge_graph");
                if (knowledgeGraph != null && !knowledgeGraph.isEmpty()) {
                    List<String> entries = new ArrayList<>();
                    String title = knowledgeGraph.getStr("title");
                    if (title != null && !title.isBlank()) {
                        entries.add("title: " + title);
                    }
                    String description = knowledgeGraph.getStr("description");
                    if (description != null && !description.isBlank()) {
                        entries.add("description: " + description);
                    }
                    if (!entries.isEmpty()) {
                        return String.join(", ", entries);
                    }
                }
                return "No search results found for query: " + query;
            }

            int endIndex = Math.min(organicResults.size(), MAX_RESULTS);
            List<Object> objects = organicResults.subList(0, endIndex);
            // 拼接搜索结果为字符串
            String result = objects.stream().map(obj -> {
                JSONObject tmpJSONObject = (JSONObject) obj;
                return tmpJSONObject.toString();
            }).collect(Collectors.joining(","));
            return result;
        } catch (Exception e) {
            return "Error searching web: " + e.getMessage();
        }
    }

    @Tool(description = "Recommend tourist attractions for a Chinese region, city or theme (uses web search).")
    public String recommendAttractions(
            @ToolParam(description = "Destination or theme, e.g. 甘肃、敦煌、三山五岳") String regionOrTheme) {
        if (regionOrTheme == null || regionOrTheme.isBlank()) {
            return "请提供目的地或主题（如省、市、景区类型），以便检索景点推荐。";
        }
        String q = regionOrTheme.trim() + " 旅游景点推荐 攻略";
        return this.searchWeb(q);
    }
}
