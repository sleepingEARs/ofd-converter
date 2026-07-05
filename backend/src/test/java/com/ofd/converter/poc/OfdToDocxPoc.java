package com.ofd.converter.poc;

import com.ofd.converter.Fixtures;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ofdrw.core.basicStructure.pageObj.Page;
import org.ofdrw.core.basicStructure.pageObj.layer.PageBlockType;
import org.ofdrw.core.basicStructure.pageObj.layer.block.TextObject;
import org.ofdrw.core.text.TextCode;
import org.ofdrw.reader.OFDReader;
import org.ofdrw.reader.PageInfo;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PoC: OFD -> DOCX feasibility.
 *
 * Explores whether ofdrw-reader can extract text from an OFD, and whether POI
 * can build a valid DOCX from it. Gates the production OFD->DOCX converter (Plan 2).
 *
 * Verified ofdrw-reader 2.3.9 API path:
 *   OFDReader(Path) -> getNumberOfPages() -> getPage(i).getContent().getLayers()
 *   -> CT_Layer.getPageBlocks() (CT_Layer extends CT_PageBlock)
 *   -> filter TextObject -> getTextCodes() -> TextCode.getContent()
 */
class OfdToDocxPoc {

    @Test
    void extractTextAndBuildDocx(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofd(tmp);
        Path docx = tmp.resolve("out.docx");

        StringBuilder extracted = new StringBuilder();
        try (OFDReader reader = new OFDReader(ofd)) {
            for (PageInfo info : reader.getPageList()) {
                Page page = info.getObj();
                if (page == null || page.getContent() == null) continue;
                for (var layer : page.getContent().getLayers()) {
                    for (PageBlockType block : layer.getPageBlocks()) {
                        if (block instanceof TextObject to) {
                            for (TextCode tc : to.getTextCodes()) {
                                String s = tc.getContent();
                                if (s != null && !s.isEmpty()) {
                                    extracted.append(s).append("\n");
                                }
                            }
                        }
                    }
                }
            }
        }

        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText(extracted.toString());
            try (var out = Files.newOutputStream(docx)) {
                doc.write(out);
            }
        }

        byte[] head = Files.readAllBytes(docx);
        assertEquals(0x50, head[0], "DOCX must be a ZIP");
        assertEquals(0x4B, head[1], "DOCX must be a ZIP");
        assertTrue(extracted.length() > 0,
            "Extraction must be non-empty; if empty, fixture text is not in TextObject/TextCode");
    }
}
