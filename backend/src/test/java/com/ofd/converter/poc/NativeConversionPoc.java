package com.ofd.converter.poc;

import com.ofd.converter.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ofdrw.converter.export.ImageExporter;
import org.ofdrw.converter.export.PDFExporterPDFBox;
import org.ofdrw.converter.export.TextExporter;
import org.ofdrw.converter.ofdconverter.ImageConverter;
import org.ofdrw.converter.ofdconverter.PDFConverter;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeConversionPoc {
    @Test void ofdToPdf(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofd(tmp), out = tmp.resolve("out.pdf");
        try (var ex = new PDFExporterPDFBox(ofd, out)) { ex.export(); }
        assertTrue(Files.size(out) > 0);
        assertEquals(0x25, Files.readAllBytes(out)[0]); // %
    }
    @Test void ofdToPng(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofd(tmp); Path outDir = tmp.resolve("img");
        Files.createDirectory(outDir);
        try (var ex = new ImageExporter(ofd, outDir, "PNG", 15d)) {
            ex.export();
            assertFalse(ex.getImgFilePaths().isEmpty());
        }
    }
    @Test void ofdToText(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofd(tmp), out = tmp.resolve("out.txt");
        try (var ex = new TextExporter(ofd, out)) { ex.export(); }
        // TextExporter may produce empty for path-glyph pages; our fixture uses text, so expect non-empty.
        String txt = Files.readString(out);
        assertFalse(txt.isBlank(), "TextExporter should extract text from text-based fixture");
    }
    @Test void pdfToOfd(@TempDir Path tmp) throws Exception {
        Path pdf = Fixtures.pdf(tmp), out = tmp.resolve("fromPdf.ofd");
        try (var c = new PDFConverter(out)) { c.convert(pdf); }
        byte[] head = Files.readAllBytes(out);
        assertEquals(0x50, head[0]); assertEquals(0x4B, head[1]); // ZIP
    }
    @Test void pngToOfd(@TempDir Path tmp) throws Exception {
        Path png = Fixtures.png(tmp), out = tmp.resolve("fromPng.ofd");
        try (var c = new ImageConverter(out)) { c.convert(png); }
        byte[] head = Files.readAllBytes(out);
        assertEquals(0x50, head[0]); assertEquals(0x4B, head[1]);
    }
}
