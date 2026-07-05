package com.ofd.converter.interceptor;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpInterceptor {
    private ClientIpInterceptor() {}

    public static String extractIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xri = req.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();
        return req.getRemoteAddr();
    }
}
