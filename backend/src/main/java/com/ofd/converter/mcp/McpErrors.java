package com.ofd.converter.mcp;

public final class McpErrors {
    private McpErrors() {}
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    public static final int NO_SESSION = -32000;
    public static final int NOT_INITIALIZED = -32001;

    /** Thrown to produce a JSON-RPC error response. */
    public static class McpException extends RuntimeException {
        public final int code;
        public McpException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}