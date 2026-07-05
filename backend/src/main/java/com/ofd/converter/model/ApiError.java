package com.ofd.converter.model;

import java.util.Map;

public record ApiError(Error error) {
    public record Error(String code, String message, Map<String, Object> details) {}

    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(new Error(code.name(), message, Map.of()));
    }
}
