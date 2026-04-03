package com.prodigal.travel.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.io.InputStream;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * @project prodigal-ai-travel
 * @author Lang
 * @since  2026/4/2
 * @Version: 1.0
 * @description 天气查询工具类
 */
public class WeatherTool {

    private static final String AD_CODE_FILE = "travel/AMap_adcode_citycode.xlsx";
    private static final String AMAP_WEATHER_URL = "https://restapi.amap.com/v3/weather/weatherInfo";
    private static final int DEFAULT_CITY_COLUMN_INDEX = 0;
    private static final int DEFAULT_ADCODE_COLUMN_INDEX = 1;
    private static final Set<String> CITY_HEADER_CANDIDATES = new HashSet<>(List.of("中文名", "city", "cityname", "name"));
    private static final Set<String> ADCODE_HEADER_CANDIDATES = new HashSet<>(List.of("adcode", "ad_code"));
    private static final int MAX_SCAN_ROWS = 5000;

    private final String amapApiKey;

    public WeatherTool(String amapApiKey) {
        this.amapApiKey = amapApiKey;
    }

    @Tool(description = "Query weather by user-provided city name.")
    public String getWeather(
            @ToolParam(description = "City name, e.g. Beijing, Shanghai.") String city,
            @ToolParam(description = "Whether to query live weather. true=live weather, false=forecast weather. Default is true.") Boolean realtime
    ) {
        if (StrUtil.isBlank(city)) {
            return "天气查询失败：城市不能为空。";
        }
        if (StrUtil.isBlank(amapApiKey)) {
            return "天气查询失败：未配置高德天气 API Key。";
        }

        boolean queryRealtime = realtime == null || realtime;
        String normalizedCity = city.trim();
        try {
            String adCode = findAdCodeByCity(normalizedCity);
            if (StrUtil.isBlank(adCode)) {
                return "天气查询失败：在 " + AD_CODE_FILE + " 中未找到城市「" + normalizedCity + "」的编码。";
            }

            HashMap<String, Object> params = new HashMap<>();
            params.put("key", amapApiKey);
            params.put("city", adCode);
            params.put("extensions", queryRealtime ? "base" : "all");
            params.put("output", "JSON");

            String weatherResp = HttpUtil.get(AMAP_WEATHER_URL, params);
            JSONObject root = JSONUtil.parseObj(weatherResp);
            if (!"1".equals(root.getStr("status")) || !"10000".equals(root.getStr("infocode"))) {
                return "天气查询失败：高德天气服务返回异常（" + root.getStr("info", "unknown") + "）。";
            }

            if (queryRealtime) {
                JSONArray lives = root.getJSONArray("lives");
                if (lives == null || lives.isEmpty()) {
                    return "天气查询失败：天气服务返回为空。";
                }
                JSONObject current = lives.getJSONObject(0);
                return "天气查询成功（实况）\n"
                        + "城市：" + current.getStr("province", "") + current.getStr("city", normalizedCity) + "\n"
                        + "天气：" + current.getStr("weather", "未知") + "\n"
                        + "气温：" + current.getStr("temperature", "") + "°C\n"
                        + "湿度：" + current.getStr("humidity", "") + "%\n"
                        + "风向：" + current.getStr("winddirection", "") + "\n"
                        + "风力：" + current.getStr("windpower", "") + "级\n"
                        + "发布时间：" + current.getStr("reporttime", "");
            }

            JSONArray forecasts = root.getJSONArray("forecasts");
            if (forecasts == null || forecasts.isEmpty()) {
                return "天气查询失败：天气预报数据为空。";
            }
            JSONObject forecast = forecasts.getJSONObject(0);
            JSONArray casts = forecast.getJSONArray("casts");
            if (casts == null || casts.isEmpty()) {
                return "天气查询失败：天气预报明细为空。";
            }

            StringBuilder sb = new StringBuilder("天气查询成功（预报）\n")
                    .append("城市：")
                    .append(forecast.getStr("province", ""))
                    .append(forecast.getStr("city", normalizedCity))
                    .append("\n")
                    .append("发布时间：")
                    .append(forecast.getStr("reporttime", ""))
                    .append("\n");

            for (int i = 0; i < casts.size(); i++) {
                JSONObject cast = casts.getJSONObject(i);
                sb.append("\n")
                        .append(cast.getStr("date", ""))
                        .append(" (周")
                        .append(cast.getStr("week", ""))
                        .append(")")
                        .append("：")
                        .append(cast.getStr("dayweather", "未知"))
                        .append("/")
                        .append(cast.getStr("nightweather", "未知"))
                        .append("，")
                        .append(cast.getStr("nighttemp", ""))
                        .append("~")
                        .append(cast.getStr("daytemp", ""))
                        .append("°C，")
                        .append("风向 ")
                        .append(cast.getStr("daywind", ""))
                        .append("/")
                        .append(cast.getStr("nightwind", ""))
                        .append("，风力 ")
                        .append(cast.getStr("daypower", ""))
                        .append("/")
                        .append(cast.getStr("nightpower", ""));
            }
            return sb.toString();
        } catch (Exception e) {
            return "天气查询失败：" + e.getMessage();
        }
    }

    private String findAdCodeByCity(String city) {
        File file = resolveAdCodeFile();
        if (file == null) {
            throw new IllegalStateException("未找到 " + AD_CODE_FILE + "，请放到 resources 目录或项目根目录。");
        }

        ExcelReader reader = ExcelUtil.getReader(file);
        try {
            int cityColumnIndex = DEFAULT_CITY_COLUMN_INDEX;
            int adCodeColumnIndex = DEFAULT_ADCODE_COLUMN_INDEX;

            List<Object> headerRow = reader.readRow(0);
            if (headerRow != null && !headerRow.isEmpty()) {
                Integer resolvedCityColumn = resolveColumnIndex(headerRow, CITY_HEADER_CANDIDATES);
                Integer resolvedAdCodeColumn = resolveColumnIndex(headerRow, ADCODE_HEADER_CANDIDATES);
                if (resolvedCityColumn != null) {
                    cityColumnIndex = resolvedCityColumn;
                }
                if (resolvedAdCodeColumn != null) {
                    adCodeColumnIndex = resolvedAdCodeColumn;
                }
            }

            for (int i = 1; i <= MAX_SCAN_ROWS; i++) {
                List<Object> row = reader.readRow(i);
                if (row == null || row.isEmpty()) {
                    continue;
                }
                String rowCity = getCell(row, cityColumnIndex);
                String rowAdCode = getCell(row, adCodeColumnIndex);
                if (StrUtil.isBlank(rowCity) || StrUtil.isBlank(rowAdCode)) {
                    continue;
                }
                if (isCityMatch(city, rowCity)) {
                    return rowAdCode.trim();
                }
            }
            return null;
        } finally {
            reader.close();
        }
    }

    private File resolveAdCodeFile() {
        try (InputStream in = ResourceUtil.getStream(AD_CODE_FILE)) {
            return FileUtil.writeFromStream(in, FileUtil.file(FileUtil.getTmpDirPath(), AD_CODE_FILE));
        } catch (Exception ignored) {
            // fall through and continue lookup in project root
        }

        File projectRootFile = FileUtil.file(AD_CODE_FILE);
        if (projectRootFile.exists()) {
            return projectRootFile;
        }
        return null;
    }

    private boolean isCityMatch(String inputCity, String rowCity) {
        String a = normalizeCityName(inputCity);
        String b = normalizeCityName(rowCity);
        return StrUtil.equals(a, b) || StrUtil.equals(a + "市", b) || StrUtil.equals(a, b + "市");
    }

    private String normalizeCityName(String city) {
        if (city == null) {
            return "";
        }
        String normalized = city.trim();
        if (normalized.endsWith("市")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String getCell(List<Object> row, int index) {
        if (row.size() <= index || row.get(index) == null) {
            return "";
        }
        return String.valueOf(row.get(index)).trim();
    }

    private Integer resolveColumnIndex(List<Object> headerRow, Set<String> candidates) {
        for (int i = 0; i < headerRow.size(); i++) {
            String header = getCell(headerRow, i);
            if (StrUtil.isBlank(header)) {
                continue;
            }
            String normalized = header.trim().toLowerCase();
            if (candidates.contains(normalized)) {
                return i;
            }
        }
        return null;
    }
}
