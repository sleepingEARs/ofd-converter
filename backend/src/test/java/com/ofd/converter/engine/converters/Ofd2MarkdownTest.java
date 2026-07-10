package com.ofd.converter.engine.converters;

import com.ofd.converter.Fixtures;
import com.ofd.converter.engine.ConvertResult;
import com.ofd.converter.engine.extract.OfdTextBlockExtractor;
import com.ofd.converter.engine.structure.MdStructureInferrer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Ofd2MarkdownTest {

    @Test
    void convertsOfdToMarkdown(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofdWithHeadings(tmp);
        Ofd2Markdown converter = new Ofd2Markdown(new OfdTextBlockExtractor(), new MdStructureInferrer());

        ConvertResult r = converter.convert(ofd, tmp, "headings.ofd", null);

        assertEquals("headings.md", r.outputFilename());
        assertEquals("single", r.outputType());
        String md = Files.readString(r.outputFile());
        assertFalse(md.isBlank());
        // ofdrw-layout fixture has uniform font size, so no heading inference fires -> output
        // is paragraphs. Assert text content + valid markdown (non-empty). Heading-by-font-size
        // is covered by MdStructureInferrerTest with synthetic blocks.
        assertTrue(md.contains("标题"));
    }

    @Test
    void rendersListSyntax(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofdWithList(tmp);
        Ofd2Markdown converter = new Ofd2Markdown(new OfdTextBlockExtractor(), new MdStructureInferrer());
        ConvertResult r = converter.convert(ofd, tmp, "list.ofd", null);
        String md = Files.readString(r.outputFile());
        assertTrue(md.contains("1.") || md.contains("- "), "Markdown must contain list syntax");
    }
}
