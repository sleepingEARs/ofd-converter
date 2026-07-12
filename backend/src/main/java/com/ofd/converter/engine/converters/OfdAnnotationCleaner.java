package com.ofd.converter.engine.converters;

import java.io.IOException;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Shared utility: pre-cleans OFD files to prevent ofdrw NPE on annotation elements
 * that lack an Appearance child (ofdrw calls getAppearance().getPageBlocks() without
 * null check). Re-zips the OFD, injecting a minimal <Appearance/> into every Annot
 * element (with any namespace prefix, e.g. ofd:Annot) that lacks one.
 */
public final class OfdAnnotationCleaner {
    private OfdAnnotationCleaner() {}

    /** Matches an <Annot> element (with optional namespace prefix) lacking an <Appearance> child. */
    private static final Pattern ANNOT_NO_APPEARANCE = Pattern.compile(
        "<(?:\\w+:)?Annot\\b[^>]*>(?:(?!(?:\\w+:)?Appearance).)*?</(?:\\w+:)?Annot>",
        Pattern.DOTALL
    );
    /** Matches a self-closing <Annot .../> (with optional namespace prefix). */
    private static final Pattern ANNOT_SELF_CLOSING = Pattern.compile(
        "<(?:\\w+:)?Annot\\b[^>]*/>"
    );

    /**
     * Creates a cleaned copy of the OFD with empty Appearance injected into Annot
     * elements that lack one. Returns the path to the cleaned OFD.
     */
    public static Path clean(Path source, Path target) throws IOException {
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
        return target;
    }

    static String injectEmptyAppearance(String xml) {
        // Self-closing: <ofd:Annot .../> -> <ofd:Annot><ofd:Appearance/></ofd:Annot>
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
        Matcher oc = ANNOT_NO_APPEARANCE.matcher(xml);
        sb = new StringBuffer();
        while (oc.find()) {
            String full = oc.group();
            String prefix = extractPrefix(full);
            String closeTag = "</" + prefix + "Annot>";
            String fixed = full.replace(closeTag, "<" + prefix + "Appearance/>" + closeTag);
            oc.appendReplacement(sb, Matcher.quoteReplacement(fixed));
        }
        oc.appendTail(sb);
        return sb.toString();
    }

    private static String extractPrefix(String tag) {
        Matcher m = Pattern.compile("<(\\w+:)?Annot").matcher(tag);
        if (m.find()) {
            return m.group(1) == null ? "" : m.group(1);
        }
        return "";
    }
}
