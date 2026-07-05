package com.ofd.converter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FixturesTest {
    @Test void generatesOfd(@TempDir Path tmp) throws Exception {
        Path p = Fixtures.ofd(tmp);
        assertTrue(Files.size(p) > 0);
        // OFD is a ZIP — first bytes are 50 4B 03 04
        byte[] head = Files.readAllBytes(p).length > 4
            ? Files.readAllBytes(p) : new byte[0];
        assertEquals(0x50, head[0]);
        assertEquals(0x4B, head[1]);
    }
    @Test void generatesPdf(@TempDir Path tmp) throws Exception {
        Path p = Fixtures.pdf(tmp);
        byte[] head = Files.readAllBytes(p);
        assertEquals(0x25, head[0]); // %
        assertEquals(0x50, head[1]); // P
    }
    @Test void generatesPng(@TempDir Path tmp) throws Exception {
        Path p = Fixtures.png(tmp);
        byte[] head = Files.readAllBytes(p);
        assertEquals((byte) 0x89, head[0]);
    }
}
