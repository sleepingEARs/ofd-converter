package com.ofd.converter.controller;

import com.ofd.converter.model.ErrorCode;

public class ApiException extends RuntimeException {
    public final ErrorCode code;
    public final int status;

    public ApiException(ErrorCode code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
