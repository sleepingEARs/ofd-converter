package com.ofd.converter.model;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND),
    FILE_EXPIRED(HttpStatus.GONE),
    TASK_FAILED(HttpStatus.CONFLICT),
    TASK_TIMEOUT(HttpStatus.REQUEST_TIMEOUT),
    STORAGE_FULL(HttpStatus.SERVICE_UNAVAILABLE),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    public final HttpStatus status;

    ErrorCode(HttpStatus s) {
        this.status = s;
    }
}
