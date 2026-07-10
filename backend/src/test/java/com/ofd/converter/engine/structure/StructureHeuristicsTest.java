package com.ofd.converter.engine.structure;

import com.ofd.converter.engine.extract.TextBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructureHeuristicsTest {

    @Test
    void bodyFontSizeIsMostFrequent() {
        List<TextBlock> blocks = List.of(
            tb(5.0, "H1"), tb(2.5, "p1"), tb(2.5, "p2"), tb(2.5, "p3"));
        assertEquals(2.5, StructureHeuristics.bodyFontSize(blocks), 0.001);
    }

    @Test
    void headingLevelBySizeRatio() {
        double body = 2.5;
        assertEquals(1, StructureHeuristics.headingLevel(5.0, body));   // 2.0x -> H1
        assertEquals(2, StructureHeuristics.headingLevel(3.5, body));   // 1.4x -> H2
        assertEquals(0, StructureHeuristics.headingLevel(2.5, body));   // body -> not heading
        assertEquals(0, StructureHeuristics.headingLevel(2.6, body));   // 1.04x, below 1.2 -> not heading
    }

    @Test
    void listMarkerDetection() {
        assertEquals(StructureHeuristics.ListMarker.ORDERED, StructureHeuristics.listMarker("1. item"));
        assertEquals(StructureHeuristics.ListMarker.UNORDERED, StructureHeuristics.listMarker("- item"));
        assertEquals(StructureHeuristics.ListMarker.UNORDERED, StructureHeuristics.listMarker("* item"));
        assertEquals(StructureHeuristics.ListMarker.ORDERED, StructureHeuristics.listMarker("① 第一"));
        assertNull(StructureHeuristics.listMarker("普通文本"));
    }

    @Test
    void detectTableFromXGridAlignment() {
        // 2 rows (y=0, y=20) x 3 cols (x=10, 50, 90)
        List<TextBlock> blocks = List.of(
            tb(10, 0, "a"), tb(50, 0, "b"), tb(90, 0, "c"),
            tb(10, 20, "d"), tb(50, 20, "e"), tb(90, 20, "f"));
        var tables = StructureHeuristics.detectTables(blocks);
        assertEquals(1, tables.size(), "one table");
        assertEquals(2, tables.get(0).size(), "2 rows");
        assertEquals(3, tables.get(0).get(0).size(), "3 columns in first row");
    }

    @Test
    void noTableForScatteredText() {
        List<TextBlock> blocks = List.of(tb(10, 0, "a"), tb(50, 0, "b"));
        assertTrue(StructureHeuristics.detectTables(blocks).isEmpty());
    }

    private static TextBlock tb(double size, String text) {
        return new TextBlock(0, 0, 0, 10, 5, size, null, text);
    }

    private static TextBlock tb(double x, double y, String text) {
        return new TextBlock(0, x, y, 20, 5, 2.5, null, text);
    }
}
