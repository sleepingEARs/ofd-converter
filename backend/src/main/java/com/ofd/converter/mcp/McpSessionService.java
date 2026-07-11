package com.ofd.converter.mcp;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class McpSessionService {

    private static final long TTL_MS = 30L * 60_000;

    private final ConcurrentHashMap<String, McpSession> sessions = new ConcurrentHashMap<>();

    public String initialize() {
        String id = UUID.randomUUID().toString();
        sessions.put(id, new McpSession(id));
        return id;
    }

    public void markInitialized(String sessionId) {
        McpSession s = sessions.get(sessionId);
        if (s != null) {
            s.setInitialized(true);
            s.setLastActivity(System.currentTimeMillis());
        }
        // idempotent: unknown session is a no-op (spec: don't error)
    }

    public Optional<McpSession> get(String sessionId) {
        if (sessionId == null) return Optional.empty();
        McpSession s = sessions.get(sessionId);
        if (s != null) s.setLastActivity(System.currentTimeMillis());
        return Optional.ofNullable(s);
    }

    public McpSession requireInitialized(String sessionId) {
        McpSession s = (sessionId == null) ? null : sessions.get(sessionId);
        if (s == null) {
            throw new McpErrors.McpException(McpErrors.NO_SESSION, "无有效会话，请先 initialize");
        }
        if (!s.isInitialized()) {
            throw new McpErrors.McpException(McpErrors.NOT_INITIALIZED, "会话未完成初始化");
        }
        s.setLastActivity(System.currentTimeMillis());
        return s;
    }

    @Scheduled(fixedRate = 5L * 60_000)
    public void cleanupExpired() {
        long cutoff = System.currentTimeMillis() - TTL_MS;
        sessions.entrySet().removeIf(e -> e.getValue().getLastActivity() < cutoff);
    }
}