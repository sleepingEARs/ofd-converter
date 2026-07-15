package com.ofd.converter.engine.structure;

import com.ofd.converter.engine.extract.TextBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OfdStructureInferrerTest {

    private final OfdStructureInferrer inferrer = new OfdStructureInferrer();

    @Test
    void infersHeadingsWithLevelsAndPreservesFontSize() {
        List<TextBlock> blocks = List.of(
            new TextBlock(0, 0, 0, 50, 8, 5.0, null, "一级标题"),
            new TextBlock(0, 0, 20, 50, 8, 3.5, null, "二级标题"),
            new TextBlock(0, 0, 40, 50, 6, 2.5, null, "正文一"),
            new TextBlock(0, 0, 50, 50, 6, 2.5, null, "正文二"));

        List<StructureElement> elements = inferrer.infer(blocks);

        assertEquals(StructureType.HEADING, elements.get(0).getType());
        assertEquals(1, elements.get(0).getLevel());
        assertEquals(5.0, elements.get(0).getFontSize(), "DOCX preserves font size");
        assertEquals(StructureType.HEADING, elements.get(1).getType());
        assertEquals(2, elements.get(1).getLevel());
        assertEquals(StructureType.PARAGRAPH, elements.get(2).getType());
        assertEquals(2.5, elements.get(2).getFontSize());
    }

    @Test
    void infersListWithOrdering() {
        List<TextBlock> blocks = List.of(
            new TextBlock(0, 0, 0, 50, 6, 2.5, null, "1. 第一项"),
            new TextBlock(0, 0, 10, 50, 6, 2.5, null, "2. 第二项"));
        List<StructureElement> elements = inferrer.infer(blocks);
        assertEquals(StructureType.LIST, elements.get(0).getType());
        assertTrue(elements.get(0).isOrdered());
        assertEquals("第一项", elements.get(0).getText());
    }

    @Test
    void degradesToParagraphOnNoStructure() {
        List<TextBlock> blocks = List.of(
            new TextBlock(0, 0, 0, 50, 6, 2.5, null, "只是一段普通文字"));
        List<StructureElement> elements = inferrer.infer(blocks);
        assertEquals(1, elements.size());
        assertEquals(StructureType.PARAGRAPH, elements.get(0).getType());
    }

    @Test
    void preservesDocumentOrderAroundTable() {
        // Source order: paragraph A, table (2x2), paragraph B.
        // Must NOT collapse to: table, paragraph A, paragraph B (the old bug).
        List<TextBlock> blocks = List.of(
            new TextBlock(0, 0, 0, 50, 6, 2.5, null, "段落A"),
            new TextBlock(0, 10, 20, 20, 5, 2.5, null, "表1a"),
            new TextBlock(0, 50, 20, 20, 5, 2.5, null, "表1b"),
            new TextBlock(0, 10, 40, 20, 5, 2.5, null, "表2a"),
            new TextBlock(0, 50, 40, 20, 5, 2.5, null, "表2b"),
            new TextBlock(0, 0, 60, 50, 6, 2.5, null, "段落B"));
        List<StructureElement> elements = inferrer.infer(blocks);
        assertEquals(3, elements.size(), "paragraph + table + paragraph");
        assertEquals(StructureType.PARAGRAPH, elements.get(0).getType());
        assertEquals("段落A", elements.get(0).getText());
        assertEquals(StructureType.TABLE, elements.get(1).getType());
        assertEquals(StructureType.PARAGRAPH, elements.get(2).getType());
        assertEquals("段落B", elements.get(2).getText());
    }
}
