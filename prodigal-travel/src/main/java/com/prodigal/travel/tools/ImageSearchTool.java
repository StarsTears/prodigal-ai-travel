package com.prodigal.travel.tools;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 图片搜索工具（Pexels API）
 * @since 2026/4/4
 */
@Slf4j
public class ImageSearchTool {

    private static final String API_URL = "https://api.pexels.com/v1/search";
    /** 与文档一致：每页最多 80，默认取若干条直链 */
    private static final int DEFAULT_PER_PAGE = 15;

    private String apiKey;

    ImageSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search royalty-free stock photos on Pexels by English keyword; returns large image URLs, comma-separated if multiple. "
            + "If the user query is not English (e.g. Chinese), call translateToEnglish first and pass the English phrase here.")
    public String searchImage(@ToolParam(description = "English search keywords for Pexels, e.g. nature, city, food. "
            + "Translate non-English queries to English before calling.") String query) {
        if (StrUtil.isBlank(query)) {
            log.warn("No query provided for image search.");
            return "";
        }
        if (StrUtil.isBlank(apiKey)) {
            log.error("Pexels API key is not configured (prodigal.pexels.api-key).");
            return "";
        }
        try {
            String body = HttpRequest.get(API_URL)
                    .header("Authorization", apiKey)
                    .form("query", query.trim())
                    .form("per_page", DEFAULT_PER_PAGE)
                    .timeout(30_000)
                    .execute()
                    .body();
            JSONObject root = JSONUtil.parseObj(body);
            JSONArray photos = root.getJSONArray("photos");
            if (photos == null || photos.isEmpty()) {
                log.warn("No photos found for query: {}", query);
                return "";
            }
            List<String> links = new ArrayList<>(photos.size());
            for (int i = 0; i < photos.size(); i++) {
                JSONObject photo = photos.getJSONObject(i);
                if (photo == null) {
                    continue;
                }
                JSONObject src = photo.getJSONObject("src");
                if (src == null) {
                    continue;
                }
                String large = src.getStr("large");
                if (StrUtil.isNotBlank(large)) {
                    links.add(large);
                }
            }
            return CollUtil.join(links, ",");
        } catch (Exception e) {
            log.error("Error searching images: {}", e.getMessage());
            return "";
        }
    }
}
