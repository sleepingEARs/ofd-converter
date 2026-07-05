package com.ofd.converter.poc;

import com.ofd.converter.Fixtures;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ofdrw.core.basicStructure.pageObj.Page;
import org.ofdrw.core.basicStructure.pageObj.layer.PageBlockType;
import org.ofdrw.core.basicStructure.pageObj.layer.block.TextObject;
import org.ofdrw.core.text.TextCode;
import org.ofdrw.converter.export.HTMLExporter;
import org.ofdrw.reader.OFDReader;
import org.ofdrw.reader.PageInfo;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PoC: OFD -> Markdown, comparing two paths to decide the production approach (Plan 2).
 *
 * Path B: ofdrw HTMLExporter -> flexmark HTML->Markdown.
 *   HTMLExporter is SVG-based, so flexmark likely yields little. Verify by inspecting HTML.
 *
 * Path A: ofdrw-reader direct extraction (same API verified in OfdToDocxPoc).
 *   TextObject exposes getSize() (font size) and getFont(), so heading inference by font
 *   size is feasible in production. This PoC extracts text + samples font sizes.
 */
class OfdToMarkdownPoc {

    @Test
    void pathB_htmlToMarkdown(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofd(tmp);
        Path html = tmp.resolve("out.html");
        try (var ex = new HTMLExporter(ofd, html)) {
            ex.export();
        }
        String htmlContent = Files.readString(html);
        String md = FlexmarkHtmlConverter.builder().build().convert(htmlContent);

        int previewLen = (int) Math.min(400, htmlContent.length());
        System.out.println("[Path B] HTML head:\n" + htmlContent.substring(0, previewLen));
        System.out.println("[Path B] HTML contains <h1>/<p>/<table>: "
            + htmlContent.contains("<h1") + "/" + htmlContent.contains("<p")
            + "/" + htmlContent.contains("<table"));
        System.out.println("[Path B] HTML contains <svg>: " + htmlContent.contains("<svg"));
        System.out.println("[Path B] MD length: " + md.length());
        System.out.println("[Path B] MD:\n" + md);

        // Finding recorded in Task 7 findings doc.
        assertTrue(htmlContent.length() > 0, "HTMLExporter must produce HTML");
    }

    @Test
    void pathA_readerStructureInference(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofd(tmp);
        StringBuilder md = new StringBuilder();
        try (OFDReader reader = new OFDReader(ofd)) {
            for (PageInfo info : reader.getPageList()) {
                Page page = info.getObj();
                if (page == null || page.getContent() == null) continue;
                for (var layer : page.getContent().getLayers()) {
                    for (PageBlockType block : layer.getPageBlocks()) {
                        if (block instanceof TextObject to) {
                            Double size = to.getSize();
                            StringBuilder text = new StringBuilder();
                            for (TextCode tc : to.getTextCodes()) {
                                if (tc.getContent() != null) text.append(tc.getContent());
                            }
                            if (text.length() > 0) {
                                md.append("[size=").append(size).append("] ")
                                  .append(text).append("\n\n");
                            }
                        }
                    }
                }
            }
        }
        System.out.println("[Path A] MD (with font sizes):\n" + md);
        assertTrue(md.length() > 0, "Path A must extract text");
    }
}
