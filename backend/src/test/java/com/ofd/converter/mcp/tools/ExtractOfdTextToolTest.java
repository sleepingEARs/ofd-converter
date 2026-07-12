package com.ofd.converter.mcp.tools;

import com.ofd.converter.Fixtures;
import com.ofd.converter.config.RetentionProperties;
import com.ofd.converter.engine.ConvertPipeline;
import com.ofd.converter.engine.converters.Ofd2Text;
import com.ofd.converter.mcp.McpErrors;
import com.ofd.converter.mcp.McpSession;
import com.ofd.converter.service.FileService;
import com.ofd.converter.service.ValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExtractOfdTextToolTest {

    @TempDir Path tmp;

    // A valid UUID used as the fixture's file_id (requireFileId now enforces UUID format).
    private static final String FIXTURE_FILE_ID = "00000000-0000-0000-0000-000000000001";

    private ExtractOfdTextTool newTool() throws Exception {
        RetentionProperties p = new RetentionProperties();
        p.setDataDir(tmp.toString());
        FileService fs = new FileService(p);
        // Store a fixture OFD so uploadFile() can find it, under a UUID-shaped dir.
        Path ofd = Fixtures.ofd(tmp);
        Files.createDirectories(tmp.resolve("uploads/" + FIXTURE_FILE_ID));
        Files.copy(ofd, tmp.resolve("uploads/" + FIXTURE_FILE_ID + "/sample.ofd"));
        ConvertPipeline pipeline = new ConvertPipeline(List.of(new Ofd2Text()));
        return new ExtractOfdTextTool(fs, pipeline, new ValidationService());
    }

    private McpSession session() {
        McpSession s = new McpSession("s1");
        s.setInitialized(true);
        return s;
    }

    @Test
    void extractsTextSynchronously() throws Exception {
        Object result = newTool().execute(Map.of("file_id", FIXTURE_FILE_ID), session());
        assertInstanceOf(Map.class, result);
        Map<?, ?> r = (Map<?, ?>) result;
        assertNotNull(r.get("text"));
        assertFalse(((String) r.get("text")).isBlank(), "extracted text must be non-empty");
    }

    @Test
    void rejectsPathTraversalFileId() {
        ExtractOfdTextTool tool = assertDoesNotThrow(() -> newTool());
        McpErrors.McpException ex = assertThrows(McpErrors.McpException.class,
            () -> tool.execute(Map.of("file_id", "../../etc"), session()));
        assertEquals(McpErrors.INVALID_PARAMS, ex.code);
    }

    @Test
    void rejectsNonUuidFileId() {
        ExtractOfdTextTool tool = assertDoesNotThrow(() -> newTool());
        McpErrors.McpException ex = assertThrows(McpErrors.McpException.class,
            () -> tool.execute(Map.of("file_id", "f1"), session()));
        assertEquals(McpErrors.INVALID_PARAMS, ex.code);
    }
}
