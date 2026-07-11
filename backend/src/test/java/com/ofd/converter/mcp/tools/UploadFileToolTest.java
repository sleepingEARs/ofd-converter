package com.ofd.converter.mcp.tools;

import com.ofd.converter.config.RetentionProperties;
import com.ofd.converter.mcp.McpSession;
import com.ofd.converter.service.FileService;
import com.ofd.converter.service.ValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UploadFileToolTest {

    @TempDir Path tmp;

    private UploadFileTool newTool() {
        RetentionProperties p = new RetentionProperties();
        p.setDataDir(tmp.toString());
        return new UploadFileTool(new FileService(p), new ValidationService());
    }

    private McpSession session() {
        McpSession s = new McpSession("s1");
        s.setInitialized(true);
        return s;
    }

    @Test
    void decodesBase64AndStoresReturningFileId() throws Exception {
        // A minimal ZIP (OFD magic) body.
        byte[] zip = {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0};
        String b64 = Base64.getEncoder().encodeToString(zip);
        Map<String, Object> args = Map.of("filename", "a.ofd", "content", b64);

        Object result = newTool().execute(args, session());

        assertInstanceOf(Map.class, result);
        Map<?, ?> r = (Map<?, ?>) result;
        assertEquals("a.ofd", r.get("filename"));
        assertEquals("OFD", r.get("source_type"));
        assertNotNull(r.get("file_id"));
    }

    @Test
    void rejectsBadBase64() throws Exception {
        Map<String, Object> args = Map.of("filename", "a.ofd", "content", "!!!notbase64!!!");
        assertThrows(Exception.class, () -> newTool().execute(args, session()));
    }
}