package com.ofd.converter.config;

import org.ofdrw.converter.FontLoader;
import org.ofdrw.font.EnvFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.awt.Font;
import java.awt.GraphicsEnvironment;

/**
 * Maps Chinese font names (宋体/SimSun/楷体/etc.) to installed Noto CJK fonts.
 *
 * ofdrw has TWO font systems:
 * 1. EnvFont (ofdrw-font) - used by AWTMaker/ImageExporter for image rendering.
 * 2. FontLoader (ofdrw-converter) - used by PdfboxMaker for PDF rendering.
 *
 * Both must be configured. EnvFont.setMapping maps font family names to AWT Font.
 * FontLoader.addAliasMapping maps OFD font names to system font file paths.
 */
@Component
public class FontMappingConfig {
    private static final Logger log = LoggerFactory.getLogger(FontMappingConfig.class);

    @PostConstruct
    public void init() {
        // --- 1. EnvFont (for ImageExporter/AWTMaker) ---
        // Trigger EnvFont.initialize() first (fMap is null until first getFont() call).
        EnvFont.getFont("dummy-trigger-init");

        Font notoSans = findFont("Noto Sans CJK SC");
        Font notoSerif = findFont("Noto Serif CJK SC");
        Font wqy = findFont("WenQuanYi Zen Hei");
        Font sansFallback = notoSans != null ? notoSans : wqy;
        Font serifFallback = notoSerif != null ? notoSerif : sansFallback;

        if (sansFallback != null) {
            registerEnvFontMapping("宋体", sansFallback);
            registerEnvFontMapping("simsun", sansFallback);
            registerEnvFontMapping("黑体", sansFallback);
            registerEnvFontMapping("simhei", sansFallback);
            registerEnvFontMapping("微软雅黑", sansFallback);
            registerEnvFontMapping("microsoft yahei", sansFallback);
            registerEnvFontMapping("楷体", serifFallback);
            registerEnvFontMapping("kaiti", serifFallback);
            registerEnvFontMapping("仿宋", serifFallback);
            registerEnvFontMapping("fangsong", serifFallback);
            EnvFont.setDefaultFont(sansFallback);
        }

        // --- 2. FontLoader (for PdfboxMaker + AWTMaker/ImageExporter) ---
        // PdfboxMaker (PDF) requires TrueType (.ttf with glyf table) - CFF/OTF causes
        // UnsupportedOperationException. AWTMaker (images) handles both.
        // Use wqy-microhei.ttf (TrueType) for PDF, NotoSansCJKsc.otf for images.
        String ttfPath = findFontFile("wqy-microhei", "wqy-zenhei");  // TrueType for PDF
        String otfPath = findFontFile("NotoSansCJKsc", ttfPath);       // OTF for images (broader coverage)

        if (ttfPath != null) {
            FontLoader fl = FontLoader.getInstance();
            String ttfFontName = "WenQuanYi Micro Hei";
            registerFontLoader(fl, "宋体", ttfFontName, ttfPath);
            registerFontLoader(fl, "SimSun", ttfFontName, ttfPath);
            registerFontLoader(fl, "黑体", ttfFontName, ttfPath);
            registerFontLoader(fl, "SimHei", ttfFontName, ttfPath);
            registerFontLoader(fl, "微软雅黑", ttfFontName, ttfPath);
            registerFontLoader(fl, "Microsoft YaHei", ttfFontName, ttfPath);
            registerFontLoader(fl, "楷体", ttfFontName, ttfPath);
            registerFontLoader(fl, "KaiTi", ttfFontName, ttfPath);
            registerFontLoader(fl, "仿宋", ttfFontName, ttfPath);
            registerFontLoader(fl, "FangSong", ttfFontName, ttfPath);
            FontLoader.loadAsDefaultFont(ttfPath);
            log.info("FontLoader default font (TrueType): {} -> {}", ttfFontName, ttfPath);
        }

        log.info("Font mappings registered: EnvFont(宋体->{}) + FontLoader(path={})",
            sansFallback != null ? sansFallback.getFontName() : "N/A", ttfPath);
    }

    private void registerEnvFontMapping(String name, Font font) {
        try {
            EnvFont.setMapping(name, font);
        } catch (Exception e) {
            log.debug("EnvFont.setMapping failed for {}: {}", name, e.getMessage());
        }
    }

    private Font findFont(String familyName) {
        for (Font f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()) {
            if (f.getFamily().equals(familyName) || f.getFontName().startsWith(familyName)) {
                return f;
            }
        }
        for (Font f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()) {
            if (f.getFamily().equalsIgnoreCase(familyName)) {
                return f;
            }
        }
        return null;
    }

    private void registerFontLoader(FontLoader fl, String ofdFontName, String sysFontName, String path) {
        try {
            fl.addAliasMapping(ofdFontName, ofdFontName, sysFontName, path);
        } catch (Exception e) {
            log.debug("FontLoader.addAliasMapping failed for {}: {}", ofdFontName, e.getMessage());
        }
    }

    /** Find a CJK font file by searching common font directories. Prefers .ttf over .ttc. */
    private String findFontFile(String... nameHints) {
        String[] dirs = {
            "/usr/share/fonts/truetype/wqy",
            "/usr/share/fonts/opentype/noto",
            "/usr/share/fonts/truetype/noto-cjk",
            "/usr/share/fonts",
        };
        for (String hint : nameHints) {
            for (String dir : dirs) {
                java.io.File d = new java.io.File(dir);
                if (!d.isDirectory()) continue;
                java.io.File[] files = d.listFiles();
                if (files == null) continue;
                // Prefer .ttf, then .otf, then .ttc
                for (String ext : new String[]{".ttf", ".otf", ".ttc"}) {
                    for (java.io.File f : files) {
                        if (f.getName().toLowerCase().contains(hint.toLowerCase())
                            && f.getName().toLowerCase().endsWith(ext)) {
                            return f.getAbsolutePath();
                        }
                    }
                }
            }
        }
        return null;
    }
}
