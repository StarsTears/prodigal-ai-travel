package com.prodigal.travel.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 获取IP定位
 * @since 2026/4/9
 */
@Slf4j
public class IPTool {

    private static final int REQUEST_TIMEOUT_MS = 5000;
    private static final String IPAPI_URL = "https://ipapi.co/json/";
    private static final String IPWHO_URL = "https://ipwho.is/";
    private static final String IPWCN_V4_URL = "https://4.ipw.cn";
    private static final String IPV4_SB_URL = "https://api-ipv4.ip.sb/ip";
    private static final String ICANHAZIP_V4_URL = "https://ipv4.icanhazip.com";

    @Tool(description = "Get current public IP and rough geolocation based on network egress.")
    public static String getIPLocation() {
        //获取 IPv4 地址
        String ipv4 = fetchIpv4Only();
        try {
            String primaryResp = HttpUtil.createGet(IPAPI_URL)
                    .timeout(REQUEST_TIMEOUT_MS)
                    .execute()
                    .body();
            String parsedPrimary = parseIpApiResponse(primaryResp, ipv4);
            if (StrUtil.isNotBlank(parsedPrimary)) {
                return parsedPrimary;
            }
        } catch (Exception e) {
            // fallback below
            log.error("IP 获取失败。{} -> {}", IPAPI_URL,e.getMessage());
        }
        try {
            String fallbackResp = HttpUtil.createGet(IPWHO_URL)
                    .timeout(REQUEST_TIMEOUT_MS)
                    .execute()
                    .body();
            String parsedFallback = parseIpWhoResponse(fallbackResp, ipv4);
            if (StrUtil.isNotBlank(parsedFallback)) {
                return parsedFallback;
            }
        } catch (Exception e) {
            // return unified error below
            log.error("IP 获取失败。{} -> {}", IPWHO_URL, e.getMessage());
        }
        if (StrUtil.isNotBlank(ipv4)) {
            return "IP 获取成功（仅 IP）\n"
                    + "IPv4：" + safe(ipv4);
        }
        return "IP 定位失败。";
    }

    private static String parseIpApiResponse(String resp, String ipv4) {
        if (StrUtil.isBlank(resp)) {
            return null;
        }
        JSONObject root = JSONUtil.parseObj(resp);
        String ip = root.getStr("ip", "");
        String city = root.getStr("city", "");
        String region = root.getStr("region", "");
        String country = root.getStr("country_name", "");
        String org = root.getStr("org", "");
        String timezone = root.getStr("timezone", "");
        if (StrUtil.isAllBlank(ip, city, region, country)) {
            return null;
        }
        String maybeV4 = isIpv4Like(ip) ? ip : ipv4;
        return "IP 定位成功\n"
                + "IPv4：" + safe(maybeV4) + "\n"
                + "定位来源IP：" + safe(ip) + "\n"
                + "国家/地区：" + safe(country) + "\n"
                + "省/州：" + safe(region) + "\n"
                + "城市：" + safe(city) + "\n"
                + "时区：" + safe(timezone) + "\n"
                + "网络运营商：" + safe(org);
    }

    private static String parseIpWhoResponse(String resp, String ipv4) {
        if (StrUtil.isBlank(resp)) {
            return null;
        }
        JSONObject root = JSONUtil.parseObj(resp);
        if (!root.getBool("success", true)) {
            return null;
        }
        String ip = root.getStr("ip", "");
        String city = root.getStr("city", "");
        String region = root.getStr("region", "");
        String country = root.getStr("country", "");
        String timezone = root.getStr("timezone", "");
        JSONObject connection = root.getJSONObject("connection");
        String isp = connection == null ? "" : connection.getStr("isp", "");
        if (StrUtil.isAllBlank(ip, city, region, country)) {
            return null;
        }
        String maybeV4 = isIpv4Like(ip) ? ip : ipv4;
        return "IP 定位成功\n"
                + "IPv4：" + safe(maybeV4) + "\n"
                + "定位来源IP：" + safe(ip) + "\n"
                + "国家/地区：" + safe(country) + "\n"
                + "省/州：" + safe(region) + "\n"
                + "城市：" + safe(city) + "\n"
                + "时区：" + safe(timezone) + "\n"
                + "网络信息：" + safe(isp);
    }

    private static boolean isIpv4Like(String ip) {
        return StrUtil.isNotBlank(ip) && ip.contains(".");
    }

    private static String fetchIpv4Only() {
        String[] sources = new String[]{IPWCN_V4_URL, IPV4_SB_URL, ICANHAZIP_V4_URL};
        for (String source : sources) {
            try {
                String raw = HttpUtil.createGet(source)
                        .timeout(REQUEST_TIMEOUT_MS)
                        .execute()
                        .body();
                if (StrUtil.isBlank(raw)) {
                    continue;
                }
                String ip = raw.trim();
                if (ip.startsWith("{")) {
                    JSONObject o = JSONUtil.parseObj(ip);
                    ip = o.getStr("ip", "").trim();
                }
                if (isIpv4Like(ip)) {
                    return ip;
                }
            } catch (Exception e) {
                // try next source
                log.error("IPv4 获取失败。{}->{}", source, e.getMessage());
            }
        }
        return null;
    }

    private static String safe(String value) {
        return StrUtil.isBlank(value) ? "未知" : value;
    }
}
