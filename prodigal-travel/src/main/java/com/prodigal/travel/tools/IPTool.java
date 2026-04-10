package com.prodigal.travel.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;


/**
 * 基于传入客户端 IP 的定位工具，避免依赖请求线程上下文。
 */
@Slf4j
public class IPTool {

    private static final int REQUEST_TIMEOUT_MS = 5000;
    private static final String IPWHO_URL = "https://ipwho.is/";
    private final ClientIpResolver clientIpResolver;

    IPTool(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Tool(description = "Locate by provided client IP. Always pass user real public IP.")
    public String getIPLocation(@ToolParam(description = "Client IP address") String clientIp) {
        log.info("{} ip tool start.ip is {}",this.getClass().getSimpleName(), clientIp);
        try {
            if (StrUtil.isBlank(clientIp)) {
                return "缺少客户端 IP 参数，无法进行定位。";
            }
            String ip = clientIp.trim();
            if (!clientIpResolver.isValidIp(ip)) {
                log.error("{},客户端 IP 格式不合法：{}",this.getClass().getSimpleName(), ip);
                return "客户端 IP 格式不合法：" + ip;
            }
            String response = HttpUtil.createGet(IPWHO_URL + ip)
                    .timeout(REQUEST_TIMEOUT_MS)
                    .execute()
                    .body();
            String result = parseIpWhoResponse(response, ip);
            return result;
        } catch (Exception e) {
            log.error("{} ip tool error:{}",this.getClass().getSimpleName(),e.getMessage());
            return "IP 获取失败，但不影响主流程，请稍后重试。";
        }
    }

    private String parseIpWhoResponse(String body, String ip) {
        if (StrUtil.isBlank(body)) {
            return "IP 获取成功：" + ip + "，但定位服务暂不可用。";
        }
        JSONObject root = JSONUtil.parseObj(body);
        if (!root.getBool("success", true)) {
            String message = root.getStr("message", root.getStr("reason", "未知错误"));
            log.error("{} ip tool error:{}",this.getClass().getSimpleName(),message);
            return "IP 获取成功：" + ip + "，但定位失败：" + message;
        }

        JSONObject connection = root.getJSONObject("connection");
        JSONObject timezone = root.getJSONObject("timezone");
        return "IP 定位成功\n"
                + "客户端 IP：" + safe(root.getStr("ip", ip)) + "\n"
                + "国家：" + safe(root.getStr("country", "")) + "\n"
                + "省/州：" + safe(root.getStr("region", "")) + "\n"
                + "城市：" + safe(root.getStr("city", "")) + "\n"
                + "邮编：" + safe(root.getStr("postal", "")) + "\n"
                + "经纬度：" + safe(root.getStr("latitude", "")) + ", " + safe(root.getStr("longitude", "")) + "\n"
                + "时区：" + safe(timezone == null ? "" : timezone.getStr("id", "")) + "\n"
                + "运营商：" + safe(connection == null ? "" : connection.getStr("isp", ""));
    }

    private String safe(String value) {
        return StrUtil.isBlank(value) ? "未知" : value;
    }
}
