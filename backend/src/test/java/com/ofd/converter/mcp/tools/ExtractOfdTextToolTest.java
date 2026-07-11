package com.ofd.converter.mcp.tools;

import com.ofd.converter.Fixtures;
import com.ofd.converter.config.RetentionProperties;
import com.ofd.converter.engine.ConvertPipeline;
import com.ofd.converter.engine.converters.Ofd2Text;
import com.ofd.converter.mcp.McpSession;
import com.ofd.converter.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExtractOfdTextToolTest {

    @TempDir Path tmp;

    private ExtractOfdTextTool newTool() throws Exception {
        RetentionProperties p = new RetentionProperties();
        p.setDataDir(tmp.toString());
        FileService fs = new FileService(p);
        // Store a fixture OFD so uploadFile() can find it.
        Path ofd = Fixtures.ofd(tmp);
        String fileId = "f1";
        Files.createDirectories(tmp.resolve("uploads/" + fileId));
        Files.copy(ofd, tmp.resolve("uploads/" + fileId + "/sample.ofd"));
        ConvertPipeline pipeline = new ConvertPipeline(List.of(new Ofd2Text()));
        return new ExtractOfdTextTool(fs, pipeline);
    }

    private McpSession session() {
        McpSession s = new McpSession("s1");
        s.setInitialized(true);
        return s;
    }

    @Test
    void extractsTextSynchronously() throws Exception {
        Object result = newTool().execute(Map.of("file_id", "f1"), session());
        assertInstanceOf(Map.class, result);
        Map<?, ?> r = (Map<?, ?>) result;
        assertNotNull(r.get("text"));
        assertFalse(((String) r.get("text")).isBlank(), "extracted text must be non-empty");
    }
}