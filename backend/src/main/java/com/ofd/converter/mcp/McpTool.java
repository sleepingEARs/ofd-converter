package com.ofd.converter.mcp;

import java.util.Map;

public interface McpTool {
    String name();
    String description();
    /** JSON Schema describing the arguments (a Map ready for Jackson to serialize). */
    Map<String, Object> inputSchema();
    /** Execute the tool. Return a JSON-serializable result (Map/List/String). */
    Object execute(Map<String, Object> args, McpSession session) throws Exception;
}