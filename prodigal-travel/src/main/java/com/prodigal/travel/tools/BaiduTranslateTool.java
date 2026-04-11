package com.prodigal.travel.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 百度翻译 Tool（aiTextTranslate：JSON 请求体 + Bearer 鉴权）
 * @see <a href="https://fanyi-api.baidu.com/doc/21">百度翻译开放平台</a>
 * @since 2026/4/11
 */
@Slf4j
public class BaiduTranslateTool {

    private static final String TRANSLATE_URL = "https://fanyi-api.baidu.com/ait/api/aiTextTranslate";

    private final String appId;
    private final String apiKey;

    BaiduTranslateTool(String appId, String apiKey) {
        this.appId = appId;
        this.apiKey = apiKey;
    }

    /**
     * 将文本译为英文；源语言由接口自动识别（from=auto）。
     */
    @Tool(description = "Translate user-provided text into English using Baidu Translate. "
            + "Source language is auto-detected. Use when English wording is needed for search, tools, or replies.")
    public String translateToEnglish(@ToolParam(description = "Text to translate to English") String text) {
        if (StrUtil.isBlank(text)) {
            log.warn("No text provided for translation.");
            return "";
        }
        if (StrUtil.isBlank(appId) || StrUtil.isBlank(apiKey)) {
            log.error("Baidu Translate is not configured (prodigal.baidu.app-id / prodigal.baidu.api-key).");
            return "Baidu Translate is not configured (prodigal.baidu.app-id / prodigal.baidu.api-key).";
        }
        String q = text.trim();
        String payload = JSONUtil.toJsonStr(JSONUtil.createObj()
                .set("appid", appId)
                .set("from", "auto")
                .set("to", "en")
                .set("q", q));
        try {
            HttpResponse response = HttpRequest.post(TRANSLATE_URL)
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .body(payload)
                    .timeout(30_000)
                    .execute();
            String body = response.body();
            if (!response.isOk()) {
                log.error("Baidu Translate HTTP " + response.getStatus() + ": " + body);
                return "Baidu Translate HTTP " + response.getStatus() + ": " + body;
            }
            JSONObject root = JSONUtil.parseObj(body);
            if (root.containsKey("error_code")) {
                log.error("Baidu Translate error: " + root.getStr("error_msg", body));
                return "Baidu Translate error: " + root.getStr("error_msg", body);
            }
            JSONArray transResult = root.getJSONArray("trans_result");
            if (transResult == null || transResult.isEmpty()) {
                log.error("No translation results found for text: " + q);
                return "";
            }
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < transResult.size(); i++) {
                JSONObject line = transResult.getJSONObject(i);
                if (line == null) {
                    continue;
                }
                String dst = line.getStr("dst");
                if (StrUtil.isNotBlank(dst)) {
                    if (out.length() > 0) {
                        out.append('\n');
                    }
                    out.append(dst);
                }
            }
            return out.toString();
        } catch (Exception e) {
            log.error("Baidu Translate request failed: " + e.getMessage());
            return "Baidu Translate request failed: " + e.getMessage();
        }
    }
}
