package com.ofd.converter.engine.structure;

import com.ofd.converter.engine.extract.TextBlock;

import java.util.*;

/**
 * Shared, stateless detection primitives used by both inferrers.
 * Each inferrer calls these and makes its own StructureElement decisions.
 */
public final class StructureHeuristics {

    public enum ListMarker { ORDERED, UNORDERED }

    private StructureHeuristics() {}

    /** Body font size = the most frequent font size among blocks (null sizes excluded). */
    public static double bodyFontSize(List<TextBlock> blocks) {
        Map<Double, Integer> freq = new HashMap<>();
        for (TextBlock b : blocks) {
            if (b.fontSize() != null) {
                double rounded = Math.round(b.fontSize() * 10.0) / 10.0;
                freq.merge(rounded, 1, Integer::sum);
            }
        }
        if (freq.isEmpty()) return 2.5;
        return freq.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey).orElse(2.5);
    }

    /**
     * Heading level for a font size given the body size.
     * Returns 0 if not a heading (size <= body * 1.2).
     * Levels: >=2.0x body -> 1, >=1.3x -> 2, >1.2x -> 3.
     */
    public static int headingLevel(double size, double body) {
        if (body <= 0 || size <= body * 1.2) return 0;
        double ratio = size / body;
        if (ratio >= 2.0) return 1;
        if (ratio >= 1.3) return 2;
        return 3;
    }

    /** Detect list marker at start of text. Returns null if none. */
    public static ListMarker listMarker(String text) {
        if (text == null || text.isBlank()) return null;
        String t = text.trim();
        if (t.matches("^[0-9]+[.、)].*")) return ListMarker.ORDERED;
        if (t.matches("^[①②③④⑤⑥⑦⑧⑨⑩].*")) return ListMarker.ORDERED;
        if (t.matches("^（[0-9]+）.*")) return ListMarker.ORDERED;
        if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("• ")) return ListMarker.UNORDERED;
        return null;
    }

    /** Strip a leading list marker (e.g. "1. foo" -> "foo", "- bar" -> "bar"). Shared by both inferrers. */
    public static String stripMarker(String text) {
        String t = text.trim();
        int space = t.indexOf(' ');
        if (space > 0 && space < 6) return t.substring(space + 1);
        return t;
    }

    /**
     * Detect tables by X-coordinate grid alignment. Groups blocks into rows (by Y proximity),
     * then finds row groups where >=2 rows share >=2 aligned X columns. Returns a list of
     * tables, each a list of rows, each a list of cells (TextBlocks). Blocks already consumed
     * by a table are excluded from further detection by the caller.
     */
    public static List<List<List<TextBlock>>> detectTables(List<TextBlock> blocks) {
        List<List<TextBlock>> rows = groupRows(blocks, 5.0);
        List<List<List<TextBlock>>> tables = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).size() < 2) continue;
            List<List<TextBlock>> tableRows = new ArrayList<>();
            tableRows.add(rows.get(i));
            for (int j = i + 1; j < rows.size(); j++) {
                if (rows.get(j).size() < 2) break;
                if (xColumnsAlign(rows.get(i), rows.get(j))) {
                    tableRows.add(rows.get(j));
                } else {
                    break;
                }
            }
            if (tableRows.size() >= 2) {
                tables.add(tableRows);
                i += tableRows.size() - 1;
            }
        }
        return tables;
    }

    /** Group blocks into rows by Y proximity (blocks within yTolerance mm are one row). */
    public static List<List<TextBlock>> groupRows(List<TextBlock> blocks, double yTolerance) {
        List<TextBlock> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparingDouble(TextBlock::y));
        List<List<TextBlock>> rows = new ArrayList<>();
        List<TextBlock> current = new ArrayList<>();
        double currentY = Double.NaN;
        for (TextBlock b : sorted) {
            if (current.isEmpty()) {
                current.add(b);
                currentY = b.y();
            } else if (Math.abs(b.y() - currentY) <= yTolerance) {
                current.add(b);
            } else {
                rows.add(current);
                current = new ArrayList<>();
                current.add(b);
                currentY = b.y();
            }
        }
        if (!current.isEmpty()) rows.add(current);
        return rows;
    }

    /** Two rows align as columns if >=2 of row b's X positions are within 5mm of row a's. */
    private static boolean xColumnsAlign(List<TextBlock> a, List<TextBlock> b) {
        // Count DISTINCT columns of `a` that align with some block in `b`. Without tracking
        // matched `a` indices, multiple `b` blocks near the same `a` column would each count,
        // causing false-positive table detection.
        Set<Integer> matchedA = new HashSet<>();
        for (TextBlock bi : b) {
            for (int ai = 0; ai < a.size(); ai++) {
                if (!matchedA.contains(ai) && Math.abs(a.get(ai).x() - bi.x()) <= 5.0) {
                    matchedA.add(ai);
                    break;
                }
            }
        }
        return matchedA.size() >= 2;
    }
}
