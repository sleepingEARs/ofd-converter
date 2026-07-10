package com.ofd.converter.engine.converters;

import com.ofd.converter.engine.*;
import com.ofd.converter.engine.extract.OfdTextBlockExtractor;
import com.ofd.converter.engine.extract.TextBlock;
import com.ofd.converter.engine.structure.MdStructureInferrer;
import com.ofd.converter.engine.structure.StructureElement;
import com.ofd.converter.engine.structure.StructureType;
import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.model.SourceType;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * OFD -> Markdown. Extracts text blocks, infers structure (MD-oriented), renders to
 * Markdown text (headings/tables/lists). For AI Agent consumption. Semantic only.
 */
@Component
public class Ofd2Markdown implements Converter {
    private final OfdTextBlockExtractor extractor;
    private final MdStructureInferrer inferrer;

    public Ofd2Markdown(OfdTextBlockExtractor extractor, MdStructureInferrer inferrer) {
        this.extractor = extractor;
        this.inferrer = inferrer;
    }

    @Override
    public SourceType source() { return SourceType.OFD; }

    @Override
    public ConvertFormat target() { return ConvertFormat.MD; }

    @Override
    public ConvertResult convert(Path source, Path outputDir, String sourceFilename, ConvertOptions opts) throws Exception {
        List<TextBlock> blocks = extractor.extract(source);
        List<StructureElement> elements = inferrer.infer(blocks);

        StringBuilder md = new StringBuilder();
        for (StructureElement e : elements) {
            switch (e.getType()) {
                case HEADING -> {
                    int level = Math.min(Math.max(e.getLevel(), 1), 3);
                    md.append("#".repeat(level)).append(' ').append(e.getText()).append("\n\n");
                }
                case PARAGRAPH -> md.append(e.getText()).append("\n\n");
                case LIST -> {
                    String prefix = e.isOrdered() ? "1. " : "- ";
                    md.append(prefix).append(e.getText()).append("\n");
                }
                case TABLE -> appendTable(md, e.getTableRows());
                case IMAGE_PLACEHOLDER -> md.append("[图片]\n\n");
            }
        }

        String base = Ofd2Pdf.basename(sourceFilename, ".ofd");
        Path out = outputDir.resolve(base + ".md");
        Files.writeString(out, md.toString());
        return new ConvertResult(out, base + ".md", Files.size(out), "single");
    }

    private static void appendTable(StringBuilder md, List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) return;
        int maxCols = rows.stream().mapToInt(List::size).max().orElse(0);
        if (maxCols == 0) return;
        for (int r = 0; r < rows.size(); r++) {
            md.append("| ");
            for (int c = 0; c < maxCols; c++) {
                String cell = c < rows.get(r).size() ? rows.get(r).get(c) : "";
                md.append(cell).append(" | ");
            }
            md.append("\n");
            if (r == 0) {
                md.append("|");
                md.append(" --- |".repeat(maxCols));
                md.append("\n");
            }
        }
        md.append("\n");
    }
}
