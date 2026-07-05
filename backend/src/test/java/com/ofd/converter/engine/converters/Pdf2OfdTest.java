package com.ofd.converter.engine.converters;

import com.ofd.converter.Fixtures;
import com.ofd.converter.engine.ConvertResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Pdf2OfdTest {
    @Test
    void converts(@TempDir Path tmp) throws Exception {
        Path pdf = Fixtures.pdf(tmp);
        Pdf2Ofd c = new Pdf2Ofd();
        ConvertResult r = c.convert(pdf, tmp, "sample.pdf", null);

        assertEquals("sample.ofd", r.outputFilename());
        byte[] head = Files.readAllBytes(r.outputFile());
        assertEquals(0x50, head[0]); // P
        assertEquals(0x4B, head[1]); // K
    }
}
