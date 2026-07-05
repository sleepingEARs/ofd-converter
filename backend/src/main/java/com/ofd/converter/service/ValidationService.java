package com.ofd.converter.service;

import com.ofd.converter.controller.ApiException;
import com.ofd.converter.model.ErrorCode;
import com.ofd.converter.model.SourceType;
import org.springframework.stereotype.Service;

@Service
public class ValidationService {
    private static final long MAX_BYTES = 50L * 1024 * 1024;

    public SourceType detect(byte[] head, String filename) {
        if (head == null || head.length < 4) throw fail();
        if (head[0] == 0x50 && head[1] == 0x4B && head[2] == 0x03 && head[3] == 0x04) {
            String n = filename == null ? "" : filename.toLowerCase();
            return n.endsWith(".docx") ? SourceType.DOCX : SourceType.OFD;
        }
        if (head[0] == 0x25 && head[1] == 0x50 && head[2] == 0x44 && head[3] == 0x46) return SourceType.PDF;
        if ((head[0] & 0xFF) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47) return SourceType.IMAGE;
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) return SourceType.IMAGE;
        throw fail();
    }

    public void validateSize(long bytes) {
        if (bytes > MAX_BYTES) throw new ApiException(ErrorCode.FILE_TOO_LARGE, "超过 50MB 限制", 400);
    }

    public String sanitizeFilename(String name) {
        String normalized = name == null ? "file" : name.replace("\\", "/");
        String base = normalized.substring(normalized.lastIndexOf('/') + 1);
        base = base.replaceAll("[\\\\/]", "").replaceAll("\\p{Cntrl}", "").trim();
        return base.isBlank() ? "file" : base;
    }

    private ApiException fail() {
        return new ApiException(ErrorCode.INVALID_FILE_TYPE, "不支持的文件类型", 400);
    }
}
