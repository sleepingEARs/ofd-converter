package com.ofd.converter.poc;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ofdrw.layout.OFDDoc;
import org.ofdrw.layout.element.Paragraph;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PoC: DOCX -> OFD feasibility.
 *
 * Explores whether POI can extract DOCX paragraphs and ofdrw-layout can build an OFD.
 * Gates the production DOCX->OFD converter (Plan 2).
 *
 * Verified ofdrw-layout 2.3.9 API: OFDDoc.add(Div) (no addPage); Paragraph(String) ctor.
 * Finding: this is text-only (no formatting/layout). Full-fidelity DOCX->OFD needs a layout engine.
 */
class DocxToOfdPoc {

    @Test
    void docxTextToOfd(@TempDir Path tmp) throws Exception {
        // Build a tiny DOCX.
        Path docx = tmp.resolve("in.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("DOCX 转 OFD 测试段落");
            try (var out = Files.newOutputStream(docx)) {
                doc.write(out);
            }
        }

        // Extract paragraphs and feed into ofdrw-layout.
        Path ofd = tmp.resolve("out.ofd");
        try (OFDDoc doc = new OFDDoc(ofd)) {
            try (XWPFDocument in = new XWPFDocument(Files.newInputStream(docx))) {
                for (XWPFParagraph p : in.getParagraphs()) {
                    String text = p.getText();
                    if (text != null && !text.isEmpty()) {
                        doc.add(new Paragraph(text));
                    }
                }
            }
        }

        byte[] head = Files.readAllBytes(ofd);
        assertEquals(0x50, head[0], "OFD must be a ZIP");
        assertEquals(0x4B, head[1], "OFD must be a ZIP");
        // Finding: text-only conversion works. Full-fidelity DOCX->OFD (formatting, layout,
        // tables, images) needs a layout engine — recommend deferring full version to a later release.
    }
}
