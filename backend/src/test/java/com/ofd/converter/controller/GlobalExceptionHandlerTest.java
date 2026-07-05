package com.ofd.converter.controller;

import com.ofd.converter.model.ApiError;
import com.ofd.converter.model.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {
    @Test
    void mapsApiExceptionToStatus() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();
        ResponseEntity<ApiError> r = h.handleApi(new ApiException(ErrorCode.FILE_TOO_LARGE, "太大", 400));
        assertEquals(400, r.getStatusCode().value());
        assertEquals("FILE_TOO_LARGE", r.getBody().error().code());
    }
}
