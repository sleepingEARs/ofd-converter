package com.ofd.converter.engine.converters;

import com.ofd.converter.Fixtures;
import com.ofd.converter.engine.ConvertResult;
import com.ofd.converter.engine.extract.OfdTextBlockExtractor;
import com.ofd.converter.engine.structure.OfdStructureInferrer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Ofd2DocxTest {

    @Test
    void convertsOfdToValidDocx(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofdWithHeadings(tmp);
        Ofd2Docx converter = new Ofd2Docx(new OfdTextBlockExtractor(), new OfdStructureInferrer());

        ConvertResult r = converter.convert(ofd, tmp, "headings.ofd", null);

        assertEquals("headings.docx", r.outputFilename());
        assertEquals("single", r.outputType());
        byte[] head = Files.readAllBytes(r.outputFile());
        assertEquals(0x50, head[0]);
        assertEquals(0x4B, head[1]);
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(r.outputFile()))) {
            assertFalse(doc.getParagraphs().isEmpty());
            String allText = doc.getParagraphs().stream()
                .map(XWPFParagraph::getText).reduce("", (a, b) -> a + b);
            assertTrue(allText.contains("标题"), "DOCX must contain heading text");
        }
    }
}
