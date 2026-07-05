package com.ofd.converter.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class ClientIpInterceptorTest {
    @Test
    void prefersXForwardedFor() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        req.setRemoteAddr("10.0.0.1");
        assertEquals("203.0.113.5", ClientIpInterceptor.extractIp(req));
    }

    @Test
    void fallsBackToRemoteAddr() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("192.168.1.9");
        assertEquals("192.168.1.9", ClientIpInterceptor.extractIp(req));
    }
}
