package com.ofd.converter.service;

import com.ofd.converter.controller.ApiException;
import com.ofd.converter.model.SourceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidationServiceTest {
    private final ValidationService v = new ValidationService();

    @Test
    void detectsOfdZip() {
        byte[] head = {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0};
        assertEquals(SourceType.OFD, v.detect(head, "a.ofd"));
    }

    @Test
    void detectsDocxZip() {
        byte[] head = {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0};
        assertEquals(SourceType.DOCX, v.detect(head, "a.docx"));
    }

    @Test
    void detectsPdf() {
        byte[] head = {0x25, 0x50, 0x44, 0x46};
        assertEquals(SourceType.PDF, v.detect(head, "a.pdf"));
    }

    @Test
    void detectsPng() {
        byte[] head = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        assertEquals(SourceType.IMAGE, v.detect(head, "a.png"));
    }

    @Test
    void detectsJpg() {
        byte[] head = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        assertEquals(SourceType.IMAGE, v.detect(head, "a.jpg"));
    }

    @Test
    void rejectsUnknown() {
        byte[] head = {1, 2, 3, 4};
        assertThrows(ApiException.class, () -> v.detect(head, "a.exe"));
    }

    @Test
    void rejectsOversize() {
        ApiException ex = assertThrows(ApiException.class, () -> v.validateSize(60L * 1024 * 1024));
        assertEquals("FILE_TOO_LARGE", ex.code.name());
    }

    @Test
    void sanitizesFilename() {
        assertEquals("a.ofd", v.sanitizeFilename("../../a.ofd"));
        assertEquals("a.ofd", v.sanitizeFilename("a.ofd "));
    }

    @Test
    void rejectsDotAndDotDotAsFilename() {
        // Bare "." / ".." would resolve to self/parent dir -> path traversal.
        assertEquals("file", v.sanitizeFilename(".."));
        assertEquals("file", v.sanitizeFilename("."));
        assertEquals("file", v.sanitizeFilename("..."));
        assertEquals("file", v.sanitizeFilename("../"));
        assertEquals("file", v.sanitizeFilename(null));
        // Leading-dot names are stripped, not rejected outright.
        assertEquals("hidden", v.sanitizeFilename(".hidden"));
    }
}
