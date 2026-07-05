package com.ofd.converter.service;

import com.ofd.converter.controller.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UploadRateLimiterTest {
    @Test
    void allowsUnderLimit() {
        UploadRateLimiter rl = new UploadRateLimiter();
        for (int i = 0; i < 20; i++) {
            assertDoesNotThrow(() -> rl.check("1.2.3.4"));
        }
    }

    @Test
    void rejectsOverLimit() {
        UploadRateLimiter rl = new UploadRateLimiter();
        for (int i = 0; i < 20; i++) rl.check("1.2.3.4");
        ApiException ex = assertThrows(ApiException.class, () -> rl.check("1.2.3.4"));
        assertEquals("TOO_MANY_REQUESTS", ex.code.name());
    }
}
