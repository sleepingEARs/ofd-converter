package com.ofd.converter.engine.converters;

import com.ofd.converter.Fixtures;
import com.ofd.converter.config.RetentionProperties;
import com.ofd.converter.engine.ConvertOptions;
import com.ofd.converter.engine.ConvertResult;
import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Ofd2ImageTest {

    @Test
    void convertsToPngZip(@TempDir Path tmp) throws Exception {
        RetentionProperties p = new RetentionProperties();
        p.setDataDir(tmp.toString());
        Ofd2Image c = new Ofd2Image(new FileService(p), ConvertFormat.PNG);

        Path ofd = Fixtures.ofd(tmp);
        ConvertResult r = c.convert(ofd, tmp, "sample.ofd", new ConvertOptions(null, 150));

        assertEquals("sample_images.zip", r.outputFilename());
        assertEquals("archive", r.outputType());
        assertTrue(Files.size(r.outputFile()) > 0);
        // Strengthened per Task 3 PoC finding: assert ZIP magic bytes.
        byte[] head = Files.readAllBytes(r.outputFile());
        assertEquals(0x50, head[0]);
        assertEquals(0x4B, head[1]);
    }
}
