package com.prodigal.mcp.tools;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 图片搜索工具（Pexels API）
 * @since 2026/4/4
 */
@Service
public class ImageSearchTool {

    private static final String API_URL = "https://api.pexels.com/v1/search";
    /** 与文档一致：每页最多 80，默认取若干条直链 */
    private static final int DEFAULT_PER_PAGE = 15;

    @Value("${pexels.api-key}")
    private String apiKey;

    @Tool(description = "Search royalty-free stock photos on Pexels by keyword; returns image URLs (large), comma-separated if multiple.")
    public String searchImage(@ToolParam(description = "Search query, e.g. nature, city, food") String query) {
        if (StrUtil.isBlank(query)) {
            return "";
        }
        if (StrUtil.isBlank(apiKey)) {
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
            return "";
        }
    }
}
