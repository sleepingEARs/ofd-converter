package com.ofd.converter.engine.converters;

import com.ofd.converter.Fixtures;
import com.ofd.converter.engine.ConvertResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Ofd2PdfTest {
    @Test
    void converts(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofd(tmp);
        Ofd2Pdf c = new Ofd2Pdf();
        ConvertResult r = c.convert(ofd, tmp, "sample.ofd", null);

        assertTrue(Files.exists(r.outputFile()));
        assertEquals("sample.pdf", r.outputFilename());
        assertEquals(0x25, Files.readAllBytes(r.outputFile())[0]); // %
        assertEquals("single", r.outputType());
    }
}
