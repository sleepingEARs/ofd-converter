package com.ofd.converter.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

public final class McpJsonRpc {
    private McpJsonRpc() {}

    public record Request(String jsonrpc, Object id, String method, Map<String, Object> params) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(String jsonrpc, Object id, Object result, Error error) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Error(int code, String message, Object data) {}

    public record Success(Object result) {}
}