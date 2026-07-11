package com.ofd.converter.mcp;

import com.ofd.converter.mcp.tools.ListFormatsTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpToolRegistryTest {

    private final McpToolRegistry registry = new McpToolRegistry(List.of(new ListFormatsTool()));

    @Test
    void listDefinitionsIncludesAllTools() {
        var defs = registry.listDefinitions();
        assertEquals(1, defs.size());
        assertEquals("list_formats", defs.get(0).get("name"));
    }

    @Test
    void callDispatchesByName() {
        Object result = registry.call("list_formats", Map.of(), session());
        assertInstanceOf(Map.class, result);
        assertTrue(((Map<?, ?>) result).containsKey("ofd"));
    }

    @Test
    void callUnknownToolThrowsInvalidParams() {
        McpErrors.McpException ex = assertThrows(McpErrors.McpException.class,
            () -> registry.call("nope", Map.of(), session()));
        assertEquals(McpErrors.METHOD_NOT_FOUND, ex.code);
    }

    private static McpSession session() {
        McpSession s = new McpSession("s1");
        s.setInitialized(true);
        return s;
    }
}