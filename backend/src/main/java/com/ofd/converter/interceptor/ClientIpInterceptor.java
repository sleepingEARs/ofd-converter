package com.ofd.converter.interceptor;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpInterceptor {
    private ClientIpInterceptor() {}

    public static String extractIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // nginx uses $proxy_add_x_forwarded_for, which APPENDS the trusted proxy's
            // remote_addr to the end. Leading entries are client-supplied and spoofable, so
            // take the last non-empty segment (the direct upstream's view of the client) rather
            // than the first. Also avoids ArrayIndexOutOfBounds on malformed values like ",".
            String[] parts = xff.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String s = parts[i].trim();
                if (!s.isEmpty()) return s;
            }
        }
        String xri = req.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();
        return req.getRemoteAddr();
    }
}
