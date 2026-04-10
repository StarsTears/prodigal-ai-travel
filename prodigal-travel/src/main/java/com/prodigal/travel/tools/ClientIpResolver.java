package com.prodigal.travel.tools;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolve client real IP behind reverse proxies / load balancers.
 */
@Slf4j
@Component
public class ClientIpResolver {

    /**
     * Header priority (high -> low):
     * 1) X-Forwarded-For: standard proxy chain, may contain multiple IPs.
     * 2) X-Real-IP: commonly set by Nginx.
     * 3) Proxy-Client-IP / WL-Proxy-Client-IP: legacy proxy headers.
     * 4) X-Original-Forwarded-For / HTTP_X_FORWARDED_FOR: compatibility.
     */
    private static final String[] CANDIDATE_HEADERS = new String[]{
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "X-Original-Forwarded-For",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_REAL_IP"
    };

    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        for (String header : CANDIDATE_HEADERS) {
            String value = request.getHeader(header);
            if (StrUtil.isBlank(value)) {
                continue;
            }
            String fromHeader = parseHeaderValue(value);
            if (StrUtil.isNotBlank(fromHeader)) {
                return normalizeLoopback(fromHeader);
            }
        }

        return normalizeLoopback(request.getRemoteAddr());
    }

    /**
     * Parse one header:
     * - For single IP headers, return that IP if valid.
     * - For X-Forwarded-For chains, return first PUBLIC valid IP.
     * - If no public IP exists, fallback to first valid IP in chain.
     */
    String parseHeaderValue(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }

        String[] parts = raw.split(",");
        List<String> validIps = new ArrayList<>();
        for (String part : parts) {
            String ip = cleanIp(part);
            if (!isValidIp(ip)) {
                continue;
            }
            if (isPublicIp(ip)) {
                return ip;
            }
            validIps.add(ip);
        }
        return validIps.isEmpty() ? null : validIps.get(0);
    }

    String cleanIp(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String ip = value.trim();
        if ("unknown".equalsIgnoreCase(ip) || "null".equalsIgnoreCase(ip)) {
            return null;
        }
        if (ip.startsWith("[")) {
            int idx = ip.indexOf(']');
            if (idx > 0) {
                ip = ip.substring(1, idx);
            }
        }
        return ip;
    }

    public boolean isValidIp(String ip) {
        if (StrUtil.isBlank(ip)) {
            return false;
        }
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isPublicIp(String ip) {
        if (!isValidIp(ip)) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                return false;
            }
            if (isCarrierGradeNatIpv4(address) || isUniqueLocalIpv6(address)) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isCarrierGradeNatIpv4(InetAddress address) {
        byte[] b = address.getAddress();
        if (b.length != 4) {
            return false;
        }
        int first = b[0] & 0xFF;
        int second = b[1] & 0xFF;
        // 100.64.0.0/10
        return first == 100 && second >= 64 && second <= 127;
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        int first = address.getAddress()[0] & 0xFF;
        // fc00::/7
        return (first & 0xFE) == 0xFC;
    }

    private String normalizeLoopback(String ip) {
        log.info("client IP: {}", ip);
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }
}
