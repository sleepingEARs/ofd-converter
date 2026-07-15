package com.ofd.converter.engine.structure;

import com.ofd.converter.engine.extract.TextBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MdStructureInferrerTest {

    private final MdStructureInferrer inferrer = new MdStructureInferrer();

    @Test
    void infersHeadingsWithoutFontSize() {
        List<TextBlock> blocks = List.of(
            new TextBlock(0, 0, 0, 50, 8, 5.0, null, "一级标题"),
            new TextBlock(0, 0, 20, 50, 8, 3.5, null, "二级标题"),
            new TextBlock(0, 0, 40, 50, 6, 2.5, null, "正文"));
        List<StructureElement> elements = inferrer.infer(blocks);
        assertEquals(StructureType.HEADING, elements.get(0).getType());
        assertEquals(1, elements.get(0).getLevel());
        assertNull(elements.get(0).getFontSize(), "MD does not preserve font size");
        assertEquals(StructureType.PARAGRAPH, elements.get(2).getType());
        assertNull(elements.get(2).getFontSize());
    }

    @Test
    void infersTableStructure() {
        // 2 rows (y=0, y=20) x 3 cols (x=10, 50, 90)
        List<TextBlock> blocks = List.of(
            new TextBlock(0, 10, 0, 20, 5, 2.5, null, "a"),
            new TextBlock(0, 50, 0, 20, 5, 2.5, null, "b"),
            new TextBlock(0, 90, 0, 20, 5, 2.5, null, "c"),
            new TextBlock(0, 10, 20, 20, 5, 2.5, null, "d"),
            new TextBlock(0, 50, 20, 20, 5, 2.5, null, "e"),
            new TextBlock(0, 90, 20, 20, 5, 2.5, null, "f"));
        List<StructureElement> elements = inferrer.infer(blocks);
        assertTrue(elements.stream().anyMatch(e -> e.getType() == StructureType.TABLE));
        StructureElement table = elements.stream()
            .filter(e -> e.getType() == StructureType.TABLE).findFirst().orElseThrow();
        assertEquals(2, table.getTableRows().size());
        assertEquals(3, table.getTableRows().get(0).size());
    }

    @Test
    void degradesToParagraph() {
        List<TextBlock> blocks = List.of(
            new TextBlock(0, 0, 0, 50, 6, 2.5, null, "普通文字"));
        List<StructureElement> elements = inferrer.infer(blocks);
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
