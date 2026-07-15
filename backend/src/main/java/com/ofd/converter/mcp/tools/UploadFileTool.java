package com.ofd.converter.mcp.tools;

import com.ofd.converter.mcp.McpErrors;
import com.ofd.converter.mcp.McpSession;
import com.ofd.converter.mcp.McpTool;
import com.ofd.converter.model.SourceType;
import com.ofd.converter.service.FileService;
import com.ofd.converter.service.ValidationService;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class UploadFileTool implements McpTool {

    private final FileService fileService;
    private final ValidationService validation;

    public UploadFileTool(FileService fileService, ValidationService validation) {
        this.fileService = fileService;
        this.validation = validation;
    }

    @Override
    public String name() { return "upload_file"; }

    @Override
    public String description() { return "上传文件（base64 编码），返回 file_id。建议 < 10MB，大文件请走 REST。"; }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
            "filename", Map.of("type", "string", "description", "文件名（含扩展名）"),
            "content", Map.of("type", "string", "description", "base64 编码的文件内容")
        ));
        schema.put("required", java.util.List.of("filename", "content"));
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> args, McpSession session) throws Exception {
        String filename = str(args.get("filename"));
        String content = str(args.get("content"));
        if (filename == null || content == null) {
            throw new McpErrors.McpException(McpErrors.INVALID_PARAMS, "缺少 filename 或 content");
        }
        // Pre-check size BEFORE decoding: base64 expands data ~4/3, so a multi-GB payload would
        // OOM during Base64.decode before validateSize ever runs.
        validation.validateSize((long) content.length() * 3 / 4);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException e) {
            throw new McpErrors.McpException(McpErrors.INVALID_PARAMS, "content 不是有效的 base64");
        }
        validation.validateSize(bytes.length);
        SourceType src = validation.detect(bytes.length >= 8 ? bytes : pad(bytes), filename);
        String safeName = validation.sanitizeFilename(filename);
        String fileId = UUID.randomUUID().toString();
        fileService.storeUpload(new ByteArrayInputStream(bytes), fileId, safeName);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("file_id", fileId);
        result.put("filename", safeName);
        result.put("size", bytes.length);
        result.put("source_type", src.name());
        return result;
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static byte[] pad(byte[] bytes) {
        byte[] out = new byte[8];
        System.arraycopy(bytes, 0, out, 0, Math.min(bytes.length, 8));
        return out;
    }
}