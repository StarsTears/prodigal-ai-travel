package com.prodigal.travel.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    @Test
    void shouldUseFirstPublicIpFromXForwardedFor() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "10.0.0.1, 172.16.3.2, 8.8.8.8, 1.1.1.1");
        req.setRemoteAddr("192.168.1.10");

        String ip = resolver.resolveClientIp(req);

        Assertions.assertEquals("8.8.8.8", ip);
    }

    @Test
    void shouldFallbackToXRealIpWhenXForwardedForInvalid() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "unknown, abc");
        req.addHeader("X-Real-IP", "114.114.114.114");
        req.setRemoteAddr("192.168.1.11");

        String ip = resolver.resolveClientIp(req);

        Assertions.assertEquals("114.114.114.114", ip);
    }

    @Test
    void shouldFallbackToRemoteAddrWhenHeadersUnavailable() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.9");

        String ip = resolver.resolveClientIp(req);

        Assertions.assertEquals("203.0.113.9", ip);
    }

    @Test
    void shouldSupportIpv6AndSkipPrivateIpv6() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "fd00::1, 2409:8c00:6c21:1051::1");
        req.setRemoteAddr("127.0.0.1");

        String ip = resolver.resolveClientIp(req);

        Assertions.assertEquals("2409:8c00:6c21:1051::1", ip);
    }

    @Test
    void shouldReturnFirstValidIfNoPublicExistsInHeader() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "10.10.10.10, 192.168.0.11");
        req.setRemoteAddr("198.51.100.10");

        String ip = resolver.resolveClientIp(req);

        Assertions.assertEquals("10.10.10.10", ip);
    }
}
