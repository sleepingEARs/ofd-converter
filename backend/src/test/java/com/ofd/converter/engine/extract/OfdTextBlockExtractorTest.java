package com.ofd.converter.engine.extract;

import com.ofd.converter.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OfdTextBlockExtractorTest {

    @Test
    void extractsBlocksFromHeadingsOfd(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofdWithHeadings(tmp);
        OfdTextBlockExtractor extractor = new OfdTextBlockExtractor();
        List<TextBlock> blocks = extractor.extract(ofd);

        assertFalse(blocks.isEmpty(), "must extract text blocks");
        // ofdrw-layout's Paragraph.setFontSize does NOT propagate into TextObject.getSize()
        // (it controls layout rendering, not the stored size attribute). So all blocks share
        // the layout default size. We assert text + geometry + non-null font size here;
        // heading-by-font-size inference is unit-tested with synthetic TextBlocks in
        // StructureHeuristicsTest / OfdStructureInferrerTest.
        assertTrue(blocks.stream().allMatch(b -> b.fontSize() != null), "font size present");
        assertTrue(blocks.stream().anyMatch(b -> b.text().contains("标题")), "heading text present");
        assertTrue(blocks.stream().anyMatch(b -> b.text().contains("正文")), "body text present");
        assertTrue(blocks.stream().allMatch(b -> b.x() >= 0 && b.y() >= 0), "coords non-negative");
    }

    @Test
    void extractsListText(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofdWithList(tmp);
        List<TextBlock> blocks = new OfdTextBlockExtractor().extract(ofd);
        assertFalse(blocks.isEmpty());
        assertTrue(blocks.stream().anyMatch(b -> b.text().startsWith("1.") || b.text().startsWith("-")));
    }
}
