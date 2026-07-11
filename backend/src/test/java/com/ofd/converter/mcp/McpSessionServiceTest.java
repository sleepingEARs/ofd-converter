package com.ofd.converter.mcp;

import com.ofd.converter.mcp.McpErrors.McpException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpSessionServiceTest {

    private final McpSessionService svc = new McpSessionService();

    @Test
    void initializeCreatesUninitializedSession() {
        String id = svc.initialize();
        assertNotNull(id);
        McpSession s = svc.get(id).orElseThrow();
        assertFalse(s.isInitialized());
    }

    @Test
    void markInitializedSetsFlag() {
        String id = svc.initialize();
        svc.markInitialized(id);
        assertTrue(svc.get(id).orElseThrow().isInitialized());
    }

    @Test
    void requireInitializedThrowsWhenNoSession() {
        McpException ex = assertThrows(McpException.class, () -> svc.requireInitialized("nope"));
        assertEquals(McpErrors.NO_SESSION, ex.code);
    }

    @Test
    void requireInitializedThrowsWhenNotInitialized() {
        String id = svc.initialize();
        McpException ex = assertThrows(McpException.class, () -> svc.requireInitialized(id));
        assertEquals(McpErrors.NOT_INITIALIZED, ex.code);
    }

    @Test
    void requireInitializedPassesAfterMark() {
        String id = svc.initialize();
        svc.markInitialized(id);
        assertDoesNotThrow(() -> svc.requireInitialized(id));
    }

    @Test
    void cleanupExpiredRemovesOldSessions() throws Exception {
        String id = svc.initialize();
        svc.markInitialized(id);
        // Force lastActivity into the past beyond TTL.
        McpSession s = svc.get(id).orElseThrow();
        s.setLastActivity(System.currentTimeMillis() - 31L * 60_000);
        svc.cleanupExpired();
        assertTrue(svc.get(id).isEmpty());
    }

    @Test
    void markInitializedIsIdempotentForUnknownSession() {
        assertDoesNotThrow(() -> svc.markInitialized("nope")); // no exception, no effect
    }
}