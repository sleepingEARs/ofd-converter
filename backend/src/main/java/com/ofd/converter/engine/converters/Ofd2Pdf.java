package com.ofd.converter.engine.converters;

import com.ofd.converter.engine.*;
import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.model.SourceType;
import org.ofdrw.converter.export.PDFExporterPDFBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Component
public class Ofd2Pdf implements Converter {
    private static final Logger log = LoggerFactory.getLogger(Ofd2Pdf.class);

    @Override
    public SourceType source() { return SourceType.OFD; }

    @Override
    public ConvertFormat target() { return ConvertFormat.PDF; }

    @Override
    public ConvertResult convert(Path source, Path outputDir, String sourceFilename, ConvertOptions opts) throws Exception {
        String base = basename(sourceFilename, ".ofd");
        Path out = outputDir.resolve(base + ".pdf");

        // Attempt 1: direct export.
        try {
            try (PDFExporterPDFBox ex = new PDFExporterPDFBox(source, out)) {
                ex.export();
            }
            return new ConvertResult(out, base + ".pdf", Files.size(out), "single");
        } catch (Exception e) {
            if (!isAnnotationNPE(e)) throw e;
            log.warn("OFD->PDF 直接转换遇到注释 NPE (ofdrw bug)，尝试剥离空注释后重试: {}", e.getMessage());
        }

        // Attempt 2: strip empty-appear
        Path cleanedOfd = outputDir.resolve(base + "_clean.ofd");
        try {
            stripEmptyAppearanceAnnotations(source, cleanedOfd);
            try (PDFExporterPDFBox ex = new PDFExporterPDFBox(cleanedOfd, out)) {
                ex.export();
            }
            return new ConvertResult(out, base + ".pdf", Files.size(out), "single");
        } catch (Exception retryEx) {
            log.error("OFD->PDF 剥离注释重试仍失败", retryEx);
            throw retryEx;
        } finally {
            Files.deleteIfExists(cleanedOfd);
        }
    }

    /** Detect the ofdrw NPE: getAppearance() returns null -> getPageBlocks() NPE. */
    private static boolean isAnnotationNPE(Throwable e) {
        Throwable cur = e;
        for (int i = 0; i < 5 && cur != null; i++) {
            String msg = cur.getMessage();
            if (msg != null && msg.contains("getAppearance")) return true;
            cur = cur.getCause();
        }
        return false;
    }

    /**
     * Strips Annot elements that have no Appearance child (the ones causing the NPE).
     * OFD is a ZIP of XML files. We re-zip, replacing any Annot element lacking
     * an Appearance child with a no-op empty Appearance (or remove the Annot entirely).
     */
    private static void stripEmptyAppearanceAnnotations(Path source, Path target) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(source));
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(new ZipEntry(entry.getName()));
                if (entry.getName().endsWith(".xml")) {
                    String xml = new String(zis.readAllBytes());
                    // Inject a minimal <Appearance/> into <Annot> elements that lack one.
                    // This prevents getAppearance() returning null.
                    xml = xml.replaceAll(
                        "(<Annot\\b[^>]*>)(?!\\s*<Appearance)",
                        "<Annot_BAK>"
                    );
                    // Simpler: replace empty Annots (no Appearance) with a complete empty Appearance
                    xml = xml.replaceAll(
                        "(<Annot\\b[^>]*>(?:(?!<Appearance).)*?</Annot>)",
                        "<Annot><Appearance/></Annot>"
                    );
                    // Also handle self-closing Annot
                    xml = xml.replaceAll(
                        "<Annot\\b[^>]*/>",
                        "<Annot><Appearance/></Annot>"
                    );
                    zos.write(xml.getBytes());
                } else {
                    zis.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
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
