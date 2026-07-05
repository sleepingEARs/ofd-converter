package com.ofd.converter.engine.converters;

import com.ofd.converter.Fixtures;
import com.ofd.converter.engine.ConvertResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Ofd2TextTest {
    @Test
    void converts(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofd(tmp);
        Ofd2Text c = new Ofd2Text();
        ConvertResult r = c.convert(ofd, tmp, "sample.ofd", null);

        assertEquals("sample.txt", r.outputFilename());
        assertFalse(Files.readString(r.outputFile()).isBlank());
    }
}
