package com.ofd.converter.service;

import com.ofd.converter.controller.ApiException;
import com.ofd.converter.model.dto.ConvertRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ConvertServiceValidationTest {

    @Test
    void rejectsPathTraversalFileId() {
        // convert() must validate file_id BEFORE touching the filesystem, so a malformed id
        // yields INVALID_REQUEST, not a wrapped NoSuchFileException / 500.
        ConvertService svc = new ConvertService(
            mock(FileService.class), new ValidationService(),
            mock(TaskService.class), mock(com.ofd.converter.engine.ConvertPipeline.class),
            mock(LogService.class), java.util.concurrent.Executors.newFixedThreadPool(1),
            new com.ofd.converter.config.RetentionProperties(), 5,
            new UploadRateLimiter());

        ConvertRequest req = new ConvertRequest("../../etc", "pdf", Map.of());
        ApiException ex = assertThrows(ApiException.class, () -> svc.convert(req, "1.2.3.4", "ua"));
        assertEquals("INVALID_REQUEST", ex.code.name());
        assertEquals(400, ex.status);
    }

    @Test
    void rejectsNullFileId() {
        ConvertService svc = new ConvertService(
            mock(FileService.class), new ValidationService(),
            mock(TaskService.class), mock(com.ofd.converter.engine.ConvertPipeline.class),
            mock(LogService.class), java.util.concurrent.Executors.newFixedThreadPool(1),
            new com.ofd.converter.config.RetentionProperties(), 5,
            new UploadRateLimiter());

        ConvertRequest req = new ConvertRequest(null, "pdf", Map.of());
        ApiException ex = assertThrows(ApiException.class, () -> svc.convert(req, "1.2.3.4", "ua"));
        assertEquals("INVALID_REQUEST", ex.code.name());
    }
}
