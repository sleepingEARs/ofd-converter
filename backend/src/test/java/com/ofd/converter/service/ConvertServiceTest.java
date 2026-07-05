package com.ofd.converter.service;

import com.ofd.converter.Fixtures;
import com.ofd.converter.config.RetentionProperties;
import com.ofd.converter.model.dto.UploadResponse;
import com.ofd.converter.engine.ConvertPipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class ConvertServiceTest {

    @Test
    void uploadStoresAndDetectsOfd(@TempDir Path tmp) throws Exception {
        RetentionProperties p = new RetentionProperties();
        p.setDataDir(tmp.toString());
        FileService fs = new FileService(p);
        ValidationService validation = new ValidationService();
        TaskService taskSvc = mock(TaskService.class);
        LogService logSvc = mock(LogService.class);
        ConvertPipeline pipeline = new ConvertPipeline(List.of());
        var exec = Executors.newFixedThreadPool(2);
        ConvertService svc = new ConvertService(fs, validation, taskSvc, pipeline, logSvc, exec, p, 5);

        byte[] ofdBytes = Files.readAllBytes(Fixtures.ofd(tmp));
        MockMultipartFile mp = new MockMultipartFile("file", "sample.ofd",
            "application/octet-stream", ofdBytes);

        UploadResponse r = svc.upload(mp, "1.2.3.4", "ua");

        assertEquals("OFD", r.sourceType());
        assertEquals("sample.ofd", r.filename());
        assertNotNull(r.fileId());
        exec.shutdownNow();
    }
}
