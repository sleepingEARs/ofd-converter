package com.ofd.converter.mcp.tools;

import com.ofd.converter.mcp.McpTool;
import com.ofd.converter.mcp.McpSession;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ListFormatsTool implements McpTool {

    @Override
    public String name() { return "list_formats"; }

    @Override
    public String description() { return "列出支持的源格式及其可转换的目标格式"; }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> args, McpSession session) {
        Map<String, List<String>> formats = new LinkedHashMap<>();
        formats.put("ofd", List.of("pdf", "png", "jpg", "txt", "docx", "md"));
        formats.put("pdf", List.of("ofd"));
        formats.put("image", List.of("ofd"));
        return formats;
    }
}