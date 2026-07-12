package com.ofd.converter.engine.structure;

import com.ofd.converter.engine.extract.TextBlock;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Markdown-oriented structure inference. Semantic only - does NOT preserve font size.
 * Calls shared StructureHeuristics for detection. Caps heading levels at 3.
 */
@Component
public class MdStructureInferrer {

    public List<StructureElement> infer(List<TextBlock> blocks) {
        if (blocks.isEmpty()) return List.of();
        double body = StructureHeuristics.bodyFontSize(blocks);

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

        for (TextBlock b : blocks) {
            if (consumed.contains(b)) continue;
            int level = (b.fontSize() == null) ? 0 : StructureHeuristics.headingLevel(b.fontSize(), body);
            if (level > 0) {
                elements.add(StructureElement.heading(b.text(), Math.min(level, 3), null));
                continue;
            }
            StructureHeuristics.ListMarker m = StructureHeuristics.listMarker(b.text());
            if (m != null) {
                elements.add(StructureElement.listItem(StructureHeuristics.stripMarker(b.text()),
                    m == StructureHeuristics.ListMarker.ORDERED));
            } else {
                elements.add(StructureElement.paragraph(b.text(), null));
            }
        }
        return elements;
    }
}
