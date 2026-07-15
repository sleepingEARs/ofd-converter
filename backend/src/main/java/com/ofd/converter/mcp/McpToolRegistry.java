package com.ofd.converter.mcp;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class McpToolRegistry {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(McpToolRegistry.class);
    private final Map<String, McpTool> tools = new LinkedHashMap<>();

    public McpToolRegistry(List<McpTool> all) {
        for (McpTool t : all) {
            tools.put(t.name(), t);
        }
    }

    public List<Map<String, Object>> listDefinitions() {
        return tools.values().stream().map(t -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("name", t.name());
            d.put("description", t.description());
            d.put("inputSchema", t.inputSchema());
            return d;
        }).toList();
    }

    public Object call(String name, Map<String, Object> args, McpSession session) {
        McpTool t = tools.get(name);
        if (t == null) {
            throw new McpErrors.McpException(McpErrors.METHOD_NOT_FOUND, "未知工具: " + name);
        }
        try {
            return t.execute(args == null ? Map.of() : args, session);
        } catch (McpErrors.McpException e) {
            throw e;
        } catch (Exception e) {
            if (e.getCause() instanceof McpErrors.McpException m) throw m;
            log.error("Tool '{}' threw unhandled exception", name, e);
            throw new McpErrors.McpException(McpErrors.INTERNAL_ERROR, "Internal error", e);
        }
    }
}