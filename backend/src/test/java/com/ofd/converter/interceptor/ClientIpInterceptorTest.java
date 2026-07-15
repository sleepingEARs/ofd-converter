package com.ofd.converter.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class ClientIpInterceptorTest {
    @Test
    void prefersTrustedSegmentOfXForwardedFor() {
        // X-Forwarded-For = "<client-supplied>, <nginx remote_addr>". The last segment is the
        // trusted proxy's view of the client; earlier segments are spoofable, so take the last.
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        req.setRemoteAddr("10.0.0.1");
        assertEquals("10.0.0.1", ClientIpInterceptor.extractIp(req));
    }

    @Test
    void ignoresSpoofedLeadingXForwardedFor() {
        // Attacker sends X-Forwarded-For: spoofed; nginx appends the real remote_addr last.
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "spoofed-ip, 198.51.100.7");
        assertEquals("198.51.100.7", ClientIpInterceptor.extractIp(req));
    }

    @Test
    void fallsBackToRemoteAddr() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("192.168.1.9");
        assertEquals("192.168.1.9", ClientIpInterceptor.extractIp(req));
    }
}
