package com.ofd.converter.engine.structure;

import com.ofd.converter.engine.extract.TextBlock;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DOCX-oriented structure inference. Preserves font size on headings/paragraphs
 * (visual approximation). Calls shared StructureHeuristics for detection.
 */
@Component
public class OfdStructureInferrer {

    public List<StructureElement> infer(List<TextBlock> blocks) {
        if (blocks.isEmpty()) return List.of();
        double body = StructureHeuristics.bodyFontSize(blocks);

        // Detect tables first; mark consumed blocks so they aren't double-emitted.
        List<List<List<TextBlock>>> tables = StructureHeuristics.detectTables(blocks);
        Set<TextBlock> consumed = new HashSet<>();
        List<StructureElement> elements = new ArrayList<>();
        for (List<List<TextBlock>> table : tables) {
            List<List<String>> rows = new ArrayList<>();
            for (List<TextBlock> row : table) {
                List<String> cells = new ArrayList<>();
                for (TextBlock cell : row) {
                    cells.add(cell.text());
                    consumed.add(cell);
                }
                rows.add(cells);
            }
            elements.add(StructureElement.table(rows));
        }

        // Remaining blocks -> heading / list / paragraph.
        for (TextBlock b : blocks) {
            if (consumed.contains(b)) continue;
            int level = (b.fontSize() == null) ? 0 : StructureHeuristics.headingLevel(b.fontSize(), body);
            if (level > 0) {
                elements.add(StructureElement.heading(b.text(), level, b.fontSize()));
                continue;
            }
            StructureHeuristics.ListMarker m = StructureHeuristics.listMarker(b.text());
            if (m != null) {
                elements.add(StructureElement.listItem(stripMarker(b.text()),
                    m == StructureHeuristics.ListMarker.ORDERED));
            } else {
                elements.add(StructureElement.paragraph(b.text(), b.fontSize()));
            }
        }
        return elements;
    }

    private static String stripMarker(String text) {
        String t = text.trim();
        int space = t.indexOf(' ');
        if (space > 0 && space < 6) return t.substring(space + 1);
        return t;
    }
}
