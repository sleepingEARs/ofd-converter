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

        // --- 2. FontLoader (for PdfboxMaker) ---
        // Map OFD font name -> system font file path.
        String notoSansPath = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc";
        String notoSerifPath = "/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc";
        String wqyPath = "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc";

        String sansPath = exists(notoSansPath) ? notoSansPath : (exists(wqyPath) ? wqyPath : null);
        String serifPath = exists(notoSerifPath) ? notoSerifPath : sansPath;

        if (sansPath != null) {
            FontLoader fl = FontLoader.getInstance();
            // addAliasMapping(ofdFontName, ofdFamilyName, systemFontName, systemFontPath)
            fl.addAliasMapping("宋体", "宋体", "Noto Sans CJK SC", sansPath);
            fl.addAliasMapping("SimSun", "SimSun", "Noto Sans CJK SC", sansPath);
            fl.addAliasMapping("黑体", "黑体", "Noto Sans CJK SC", sansPath);
            fl.addAliasMapping("SimHei", "SimHei", "Noto Sans CJK SC", sansPath);
            fl.addAliasMapping("微软雅黑", "微软雅黑", "Noto Sans CJK SC", sansPath);
            fl.addAliasMapping("Microsoft YaHei", "Microsoft YaHei", "Noto Sans CJK SC", sansPath);
            if (serifPath != null) {
                fl.addAliasMapping("楷体", "楷体", "Noto Serif CJK SC", serifPath);
                fl.addAliasMapping("KaiTi", "KaiTi", "Noto Serif CJK SC", serifPath);
                fl.addAliasMapping("仿宋", "仿宋", "Noto Serif CJK SC", serifPath);
                fl.addAliasMapping("FangSong", "FangSong", "Noto Serif CJK SC", serifPath);
            }
            FontLoader.loadAsDefaultFont(sansPath);
        }

        log.info("Font mappings registered: EnvFont(宋体->{}) + FontLoader(path={})",
            sansFallback != null ? sansFallback.getFontName() : "N/A", sansPath);
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

    private boolean exists(String path) {
        return java.nio.file.Files.exists(java.nio.file.Paths.get(path));
    }
}
