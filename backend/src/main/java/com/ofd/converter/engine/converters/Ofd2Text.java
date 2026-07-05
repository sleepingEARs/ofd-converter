package com.ofd.converter.engine.converters;

import com.ofd.converter.engine.*;
import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.model.SourceType;
import org.ofdrw.converter.export.TextExporter;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class Ofd2Text implements Converter {
    @Override
    public SourceType source() { return SourceType.OFD; }

    @Override
    public ConvertFormat target() { return ConvertFormat.TXT; }

    @Override
    public ConvertResult convert(Path source, Path outputDir, String sourceFilename, ConvertOptions opts) throws Exception {
        String base = Ofd2Pdf.basename(sourceFilename, ".ofd");
        Path out = outputDir.resolve(base + ".txt");
        try (TextExporter ex = new TextExporter(source, out)) {
            ex.export();
        }
        return new ConvertResult(out, base + ".txt", Files.size(out), "single");
    }
}
