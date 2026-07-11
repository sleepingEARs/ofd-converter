package com.ofd.converter.mcp;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class McpSession {
    private final String sessionId;
    private boolean initialized;
    private final long createdAt;
    private long lastActivity;

    public McpSession(String sessionId) {
        this.sessionId = sessionId;
        this.initialized = false;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.lastActivity = now;
    }
}