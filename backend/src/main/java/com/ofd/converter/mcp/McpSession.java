package com.ofd.converter.mcp;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class McpSession {
    private final String sessionId;
    private volatile boolean initialized;
    private final long createdAt;
    private volatile long lastActivity;

    public McpSession(String sessionId) {
        this.sessionId = sessionId;
        this.initialized = false;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.lastActivity = now;
    }
}