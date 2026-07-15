package com.ofd.converter.engine.structure;

import com.ofd.converter.engine.extract.TextBlock;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        // Map each table to its first cell so the table is emitted at its original document
        // position (where that first cell appears), rather than dumping all tables up front
        // and destroying document ordering.
        Map<TextBlock, List<List<TextBlock>>> tableByFirstCell = new HashMap<>();
        Set<TextBlock> consumed = new HashSet<>();
        for (List<List<TextBlock>> table : tables) {
            tableByFirstCell.put(table.get(0).get(0), table);
            for (List<TextBlock> row : table) {
                consumed.addAll(row);
            }
        }

        List<StructureElement> elements = new ArrayList<>();
        for (TextBlock b : blocks) {
            List<List<TextBlock>> table = tableByFirstCell.get(b);
            if (table != null) {
                List<List<String>> rows = new ArrayList<>();
                for (List<TextBlock> row : table) {
                    List<String> cells = new ArrayList<>();
                    for (TextBlock cell : row) {
                        cells.add(cell.text());
                    }
                    rows.add(cells);
                }
                elements.add(StructureElement.table(rows));
                continue;
            }
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
