package com.ofd.converter.mcp.tools;

import com.ofd.converter.mcp.McpErrors;
import com.ofd.converter.mcp.McpSession;
import com.ofd.converter.mcp.McpTool;
import com.ofd.converter.model.dto.ConvertRequest;
import com.ofd.converter.model.dto.ConvertResponse;
import com.ofd.converter.service.ConvertService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ConvertOfdTool implements McpTool {

    private final ConvertService convertService;

    public ConvertOfdTool(ConvertService convertService) {
        this.convertService = convertService;
    }

    @Override
    public String name() { return "convert_ofd"; }

    @Override
    public String description() { return "发起格式转换（异步），返回 task_id。用 get_task_status 轮询状态。"; }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
            "file_id", Map.of("type", "string"),
            "target_format", Map.of("type", "string", "description", "pdf|png|jpg|txt|docx|md|ofd"),
            "options", Map.of("type", "object", "description", "可选: pages, dpi")
        ));
        schema.put("required", java.util.List.of("file_id", "target_format"));
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> args, McpSession session) {
        @SuppressWarnings("unchecked")
        Map<String, Object> options = (Map<String, Object>) args.get("options");
        ConvertResponse r = convertService.convert(
            new ConvertRequest(str(args.get("file_id")), str(args.get("target_format")), options),
            null, null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task_id", r.taskId());
        result.put("status", r.status());
        return result;
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
}