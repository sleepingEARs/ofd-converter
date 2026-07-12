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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Component
public class Ofd2Pdf implements Converter {
    private static final Logger log = LoggerFactory.getLogger(Ofd2Pdf.class);

    /** Matches an <Annot> element (with optional namespace prefix) lacking an <Appearance> child. */
    private static final Pattern ANNOT_NO_APPEARANCE = Pattern.compile(
        "<(?:\\w+:)?Annot\\b[^>]*>(?:(?!(?:\\w+:)?Appearance).)*?</(?:\\w+:)?Annot>",
        Pattern.DOTALL
    );
    /** Matches a self-closing <Annot .../> (with optional namespace prefix). */
    private static final Pattern ANNOT_SELF_CLOSING = Pattern.compile(
        "<(?:\\w+:)?Annot\\b[^>]*/>"
    );

    @Override
    public SourceType source() { return SourceType.OFD; }

    @Override
    public ConvertFormat target() { return ConvertFormat.PDF; }

    @Override
    public ConvertResult convert(Path source, Path outputDir, String sourceFilename, ConvertOptions opts) throws Exception {
        String base = basename(sourceFilename, ".ofd");
        Path out = outputDir.resolve(base + ".pdf");

        // Pre-clean: inject <Appearance/> into Annot elements that lack one, preventing
        // ofdrw's NPE (Annot.getAppearance() returns null -> getPageBlocks() NPE).
        // Real-world OFD files (e.g. from suwell renderer) have Watermark/PageNumber
        // annotations without Appearance, triggering the bug.
        Path cleanedOfd = outputDir.resolve(base + "_clean.ofd");
        try {
            stripEmptyAppearanceAnnotations(source, cleanedOfd);
            try (PDFExporterPDFBox ex = new PDFExporterPDFBox(cleanedOfd, out)) {
                ex.export();
            }
            return new ConvertResult(out, base + ".pdf", Files.size(out), "single");
        } finally {
            Files.deleteIfExists(cleanedOfd);
        }
    }

    /**
     * Re-zips the OFD, injecting a minimal <Appearance/> into every <Annot> element
     * (with any namespace prefix, e.g. ofd:Annot) that lacks one.
     */
    private static void stripEmptyAppearanceAnnotations(Path source, Path target) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(source));
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(new ZipEntry(entry.getName()));
                if (entry.getName().endsWith(".xml")) {
                    String xml = new String(zis.readAllBytes());
                    xml = injectEmptyAppearance(xml);
                    zos.write(xml.getBytes());
                } else {
                    zis.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
    }

    /**
     * For every <[prefix:]Annot> element lacking an <[prefix:]Appearance> child, inject
     * an empty <[prefix:]Appearance/> before the closing tag. Handles namespace prefixes
     * (ofd:Annot) and self-closing tags.
     */
    static String injectEmptyAppearance(String xml) {
        // Self-closing: <ofd:Annet .../> -> <ofd:Annot><ofd:Appearance/></ofd:Annot>
        Matcher sc = ANNOT_SELF_CLOSING.matcher(xml);
        StringBuffer sb = new StringBuffer();
        while (sc.find()) {
            String prefix = extractPrefix(sc.group());
            sc.appendReplacement(sb, Matcher.quoteReplacement(
                "<" + prefix + "Annot><" + prefix + "Appearance/></" + prefix + "Annot>"));
        }
        sc.appendTail(sb);
        xml = sb.toString();

        // Open-close without Appearance: <ofd:Annot ...>content</ofd:Annot>
        // Inject <ofd:Appearance/> before </ofd:Annot>
        Matcher oc = ANNOT_NO_APPEARANCE.matcher(xml);
        sb = new StringBuffer();
        while (oc.find()) {
            String full = oc.group();
            String prefix = extractPrefix(full);
            // Insert <prefix:Appearance/> before </prefix:Annot>
            String closeTag = "</" + prefix + "Annot>";
            String fixed = full.replace(closeTag, "<" + prefix + "Appearance/>" + closeTag);
            oc.appendReplacement(sb, Matcher.quoteReplacement(fixed));
        }
        oc.appendTail(sb);
        return sb.toString();
    }

    /** Extract the namespace prefix from an Annot tag, e.g. "ofd:" from "<ofd:Annot>". */
    private static String extractPrefix(String tag) {
        Matcher m = Pattern.compile("<(\\w+:)?Annot").matcher(tag);
        if (m.find()) {
            return m.group(1) == null ? "" : m.group(1);
        }
        return "";
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
