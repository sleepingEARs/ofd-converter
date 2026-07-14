package com.ofd.converter.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofd.converter.interceptor.ClientIpInterceptor;
import com.ofd.converter.mcp.McpErrors;
import com.ofd.converter.mcp.McpJsonRpc;
import com.ofd.converter.mcp.McpSession;
import com.ofd.converter.mcp.McpSessionService;
import com.ofd.converter.mcp.McpToolRegistry;
import com.ofd.converter.model.OperationType;
import com.ofd.converter.service.LogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class McpController {

    private static final String PROTOCOL_VERSION = "2025-06-18";

    private final McpSessionService sessions;
    private final McpToolRegistry tools;
    private final LogService logService;
    private final ObjectMapper om;

    public McpController(McpSessionService sessions, McpToolRegistry tools, LogService logService, ObjectMapper om) {
        this.sessions = sessions;
        this.tools = tools;
        this.logService = logService;
        this.om = om;
    }

    @PostMapping("/mcp")
    public ResponseEntity<?> handle(@RequestBody(required = false) String raw, HttpServletRequest req) {
        McpJsonRpc.Request request;
        try {
            request = om.readValue(raw, McpJsonRpc.Request.class);
        } catch (Exception e) {
            // HTTP 200 + JSON-RPC error envelope (not 4xx): MCP clients read the
            // envelope, not the HTTP status. Consistent with all other error paths.
            return json(new McpJsonRpc.Response("2.0", null, null,
                new McpJsonRpc.Error(McpErrors.PARSE_ERROR, "解析错误", null)), 200);
        }

        String sid = req.getHeader("Mcp-Session-Id");
        if (sid == null && request.params() != null) {
            Object meta = request.params().get("_meta");
            if (meta instanceof Map<?, ?> m && m.get("sessionId") instanceof String s) sid = s;
        }

        // notifications (no id) -> 202, no body
        if (request.id() == null) {
            if ("notifications/initialized".equals(request.method())) {
                if (sid != null) sessions.markInitialized(sid);
            }
            return ResponseEntity.accepted().build();
        }

        try {
            Object result = switch (request.method() == null ? "" : request.method()) {
                case "initialize" -> {
                    String newSid = sessions.initialize();
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("protocolVersion", PROTOCOL_VERSION);
                    r.put("capabilities", Map.of("tools", Map.of()));
                    r.put("serverInfo", Map.of("name", "ofd-converter", "version", "0.1.0"));
                    r.put("_meta", Map.of("sessionId", newSid));
                    // also return sessionId in a header
                    yield new WithHeader(newSid, r);
                }
                case "tools/list" -> {
                    sessions.requireInitialized(sid);
                    yield Map.of("tools", tools.listDefinitions());
                }
                case "tools/call" -> {
                    McpSession session = sessions.requireInitialized(sid);
                    yield handleToolCall(request, session, req);
                }
                default -> throw new McpErrors.McpException(McpErrors.METHOD_NOT_FOUND, "未知方法: " + request.method());
            };
            // initialize carries a header
            if (result instanceof WithHeader wh) {
                McpJsonRpc.Response resp = new McpJsonRpc.Response("2.0", request.id(), wh.body, null);
                return ResponseEntity.ok().header("Mcp-Session-Id", wh.sessionId).body(resp);
            }
            return json(new McpJsonRpc.Response("2.0", request.id(), result, null), 200);
        } catch (McpErrors.McpException e) {
            return json(new McpJsonRpc.Response("2.0", request.id(), null,
                new McpJsonRpc.Error(e.code, e.getMessage(), null)), 200);
        } catch (Exception e) {
            return json(new McpJsonRpc.Response("2.0", request.id(), null,
                new McpJsonRpc.Error(McpErrors.INTERNAL_ERROR, e.getMessage(), null)), 200);
        }
    }

    private Object handleToolCall(McpJsonRpc.Request request, McpSession session, HttpServletRequest req) {
        Object name = null, arguments = Map.of();
        if (request.params() != null) {
            name = request.params().get("name");
            Object args = request.params().get("arguments");
            if (args instanceof Map<?, ?> a) arguments = a;
        }
        if (!(name instanceof String toolName)) {
            throw new McpErrors.McpException(McpErrors.INVALID_PARAMS, "缺少工具名");
        }
        long start = System.currentTimeMillis();
        String ip = ClientIpInterceptor.extractIp(req);
        String ua = req.getHeader("User-Agent");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> argsMap = (Map<String, Object>) arguments;
            Object toolResult = tools.call(toolName, argsMap, session);
            // MCP wraps tool results as content[{type:text, text:<json>}].
            String text;
            try { text = om.writeValueAsString(toolResult); } catch (Exception e) { text = String.valueOf(toolResult); }

            // best-effort: extract file_id/task_id for logging from the tool result.
            String fileId = field(toolResult, "file_id");
            String taskId = field(toolResult, "task_id");
            logService.record(OperationType.MCP_CALL, ip, fileId, taskId, null, toolName, "SUCCESS",
                System.currentTimeMillis() - start, null, ua);

            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("content", List.of(Map.of("type", "text", "text", text)));
            return wrapped;
        } catch (McpErrors.McpException e) {
            logService.record(OperationType.MCP_CALL, ip, null, null, null, toolName, "FAILED",
                System.currentTimeMillis() - start, e.getMessage(), ua);
            throw e;
        } catch (Exception e) {
            logService.record(OperationType.MCP_CALL, ip, null, null, null, toolName, "FAILED",
                System.currentTimeMillis() - start, e.getMessage(), ua);
            throw new McpErrors.McpException(McpErrors.INTERNAL_ERROR, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static String field(Object o, String key) {
        if (o instanceof Map m) {
            Object v = ((Map<String, Object>) m).get(key);
            return v == null ? null : v.toString();
        }
        return null;
    }

    private ResponseEntity<?> json(Object body, int status) {
        return ResponseEntity.status(status).body(body);
    }

    private record WithHeader(String sessionId, Object body) {}
}