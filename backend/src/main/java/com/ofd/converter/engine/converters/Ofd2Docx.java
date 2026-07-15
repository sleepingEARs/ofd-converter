package com.ofd.converter.engine.converters;

import com.ofd.converter.engine.*;
import com.ofd.converter.engine.extract.OfdTextBlockExtractor;
import com.ofd.converter.engine.extract.TextBlock;
import com.ofd.converter.engine.structure.OfdStructureInferrer;
import com.ofd.converter.engine.structure.StructureElement;
import com.ofd.converter.engine.structure.StructureType;
import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.model.SourceType;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * OFD -> DOCX. Extracts text blocks, infers structure (DOCX-oriented), renders to a POI
 * XWPFDocument. Preserves font size for visual approximation. Lossy on complex layouts.
 */
@Component
public class Ofd2Docx implements Converter {
    private final OfdTextBlockExtractor extractor;
    private final OfdStructureInferrer inferrer;

    public Ofd2Docx(OfdTextBlockExtractor extractor, OfdStructureInferrer inferrer) {
        this.extractor = extractor;
        this.inferrer = inferrer;
    }

    @Override
    public SourceType source() { return SourceType.OFD; }

    @Override
    public ConvertFormat target() { return ConvertFormat.DOCX; }

    @Override
    public ConvertResult convert(Path source, Path outputDir, String sourceFilename, ConvertOptions opts) throws Exception {
        List<TextBlock> blocks = extractor.extract(source);
        List<StructureElement> elements = inferrer.infer(blocks);

        String base = Ofd2Pdf.basename(sourceFilename, ".ofd");
        Path out = outputDir.resolve(base + ".docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            for (StructureElement e : elements) {
                switch (e.getType()) {
                    case HEADING -> {
                        XWPFParagraph p = doc.createParagraph();
                        p.setStyle("Heading" + Math.min(Math.max(e.getLevel(), 1), 3));
                        XWPFRun run = p.createRun();
                        run.setText(e.getText());
                        if (e.getFontSize() != null) applyFontSize(run, e.getFontSize());
                    }
                    case PARAGRAPH -> {
                        XWPFParagraph p = doc.createParagraph();
                        XWPFRun run = p.createRun();
                        run.setText(e.getText());
                        if (e.getFontSize() != null) applyFontSize(run, e.getFontSize());
                    }
                    case LIST -> {
                        XWPFParagraph p = doc.createParagraph();
                        // Simple bullet prefix (POI numbering config needs a numbering definitions part;
                        // prefix keeps it editable without that overhead).
                        p.createRun().setText("• " + e.getText());
                    }
                    case TABLE -> {
                        if (e.getTableRows() != null && !e.getTableRows().isEmpty()) {
                            // Use the max column count across rows so merged/sparse rows don't
                            // truncate cells or index out of bounds.
                            int cols = e.getTableRows().stream().mapToInt(List::size).max().orElse(0);
                            if (cols > 0) {
                                XWPFTable table = doc.createTable(e.getTableRows().size(), cols);
                                for (int r = 0; r < e.getTableRows().size(); r++) {
                                    List<String> cells = e.getTableRows().get(r);
                                    for (int c = 0; c < cols; c++) {
                                        String cell = c < cells.size() ? cells.get(c) : "";
                                        table.getRow(r).getCell(c).setText(cell);
                                    }
                                }
                            }
                        }
                    }
                    case IMAGE_PLACEHOLDER -> {
                        XWPFParagraph p = doc.createParagraph();
                        p.createRun().setText(e.getText());
                    }
                }
            }
            try (var os = Files.newOutputStream(out)) {
                doc.write(os);
            }
        }
        return new ConvertResult(out, base + ".docx", Files.size(out), "single");
    }

    /** OFD font size is in mm; convert to points (1 mm = 2.83465 pt) for POI. */
    private static void applyFontSize(XWPFRun run, double fontSizeMm) {
        run.setFontSize(fontSizeMm * 2.83465);
    }
}
