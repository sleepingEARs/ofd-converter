package com.ofd.converter.engine.converters;

import com.ofd.converter.engine.*;
import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.model.SourceType;
import org.ofdrw.converter.export.PDFExporterPDFBox;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class Ofd2Pdf implements Converter {
    @Override
    public SourceType source() { return SourceType.OFD; }

    @Override
    public ConvertFormat target() { return ConvertFormat.PDF; }

    @Override
    public ConvertResult convert(Path source, Path outputDir, String sourceFilename, ConvertOptions opts) throws Exception {
        String base = basename(sourceFilename, ".ofd");
        Path out = outputDir.resolve(base + ".pdf");
        try (PDFExporterPDFBox ex = new PDFExporterPDFBox(source, out)) {
            ex.export();
        }
        return new ConvertResult(out, base + ".pdf", Files.size(out), "single");
    }

    /** Strips path and the given extension from a filename, returning a safe base name. */
    static String basename(String filename, String ext) {
        String n = filename == null ? "file" : filename;
        String normalized = n.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) normalized = normalized.substring(slash + 1);
        if (normalized.toLowerCase().endsWith(ext)) {
            normalized = normalized.substring(0, normalized.length() - ext.length());
        }
        return normalized.isBlank() ? "file" : normalized;
    }
}
