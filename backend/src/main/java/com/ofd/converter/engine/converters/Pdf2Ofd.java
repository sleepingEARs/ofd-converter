package com.ofd.converter.engine.converters;

import com.ofd.converter.engine.*;
import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.model.SourceType;
import org.ofdrw.converter.ofdconverter.PDFConverter;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class Pdf2Ofd implements Converter {
    @Override
    public SourceType source() { return SourceType.PDF; }

    @Override
    public ConvertFormat target() { return ConvertFormat.OFD; }

    @Override
    public ConvertResult convert(Path source, Path outputDir, String sourceFilename, ConvertOptions opts) throws Exception {
        String base = Ofd2Pdf.basename(sourceFilename, ".pdf");
        Path out = outputDir.resolve(base + ".ofd");
        try (PDFConverter c = new PDFConverter(out)) {
            c.convert(source);
        }
        return new ConvertResult(out, base + ".ofd", Files.size(out), "single");
    }
}
