package com.ofd.converter.mcp.tools;

import com.ofd.converter.mcp.McpSession;
import com.ofd.converter.mcp.McpTool;
import com.ofd.converter.model.Task;
import com.ofd.converter.model.TaskStatus;
import com.ofd.converter.service.TaskService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GetTaskStatusTool implements McpTool {

    private final TaskService taskService;

    public GetTaskStatusTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public String name() { return "get_task_status"; }

    @Override
    public String description() { return "查询转换任务状态"; }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of("task_id", Map.of("type", "string")),
            "required", java.util.List.of("task_id")
        );
    }

    @Override
    public Object execute(Map<String, Object> args, McpSession session) {
        Task t = taskService.get(str(args.get("task_id")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task_id", t.getId());
        result.put("status", t.getStatus().toLowerCase());
        result.put("download_url", TaskStatus.DONE.name().equals(t.getStatus()) ? "/api/download/" + t.getId() : null);
        result.put("error", t.getErrorMessage());
        result.put("warning", t.getWarning());
        return result;
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
}