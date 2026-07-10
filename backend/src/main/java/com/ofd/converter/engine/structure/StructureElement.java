package com.ofd.converter.engine.structure;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * One inferred structural element. Used by both DOCX and Markdown renderers.
 * - HEADING: text + level (1-3) + fontSize (DOCX preserves; MD ignores)
 * - PARAGRAPH: text + fontSize (DOCX preserves)
 * - TABLE: tableRows (list of rows, each a list of cell texts)
 * - LIST: text + ordered (true=ordered, false=unordered)
 * - IMAGE_PLACEHOLDER: text = "[图片]"
 */
@Getter
@Setter
public class StructureElement {
    private StructureType type;
    private String text;
    private int level;
    private Double fontSize;
    private List<List<String>> tableRows;
    private boolean ordered;

    public StructureElement(StructureType type) {
        this.type = type;
    }

    public static StructureElement heading(String text, int level, Double fontSize) {
        StructureElement e = new StructureElement(StructureType.HEADING);
        e.text = text;
        e.level = level;
        e.fontSize = fontSize;
        return e;
    }

    public static StructureElement paragraph(String text, Double fontSize) {
        StructureElement e = new StructureElement(StructureType.PARAGRAPH);
        e.text = text;
        e.fontSize = fontSize;
        return e;
    }

    public static StructureElement table(List<List<String>> rows) {
        StructureElement e = new StructureElement(StructureType.TABLE);
        e.tableRows = new ArrayList<>(rows);
        return e;
    }

    public static StructureElement listItem(String text, boolean ordered) {
        StructureElement e = new StructureElement(StructureType.LIST);
        e.text = text;
        e.ordered = ordered;
        return e;
    }

    public static StructureElement imagePlaceholder() {
        StructureElement e = new StructureElement(StructureType.IMAGE_PLACEHOLDER);
        e.text = "[图片]";
        return e;
    }
}
