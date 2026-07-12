package com.ofd.converter.config;

import org.ofdrw.font.EnvFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.awt.Font;
import java.awt.GraphicsEnvironment;

/**
 * Maps Chinese font names (宋体/SimSun/楷体/etc.) to installed Noto CJK fonts.
 * ofdrw's EnvFont looks up fonts by the name in the OFD file; without these mappings,
 * 宋体/SimSun etc. are not found and Chinese renders as boxes (tofu).
 */
@Component
public class FontMappingConfig {
    private static final Logger log = LoggerFactory.getLogger(FontMappingConfig.class);

    @PostConstruct
    public void init() {
        // Trigger EnvFont.initialize() first (it's lazy - fMap is null until then).
        // Calling getFont() with any name forces initialization.
        EnvFont.getFont("dummy-trigger-init");

        // Find the Noto CJK font (sans + serif).
        Font notoSans = findFont("Noto Sans CJK SC");
        Font notoSerif = findFont("Noto Serif CJK SC");
        Font wqy = findFont("WenQuanYi Zen Hei");

        Font sansFallback = notoSans != null ? notoSans : wqy;
        Font serifFallback = notoSerif != null ? notoSerif : sansFallback;

        if (sansFallback == null) {
            log.warn("No CJK font found - Chinese text may render as boxes");
            return;
        }

        // Map common Chinese font names to Noto CJK.
        EnvFont.setMapping("宋体", sansFallback);
        EnvFont.setMapping("simsun", sansFallback);
        EnvFont.setMapping("黑体", sansFallback);
        EnvFont.setMapping("simhei", sansFallback);
        EnvFont.setMapping("微软雅黑", sansFallback);
        EnvFont.setMapping("microsoft yahei", sansFallback);
        EnvFont.setMapping("楷体", serifFallback);
        EnvFont.setMapping("kaiti", serifFallback);
        EnvFont.setMapping("仿宋", serifFallback);
        EnvFont.setMapping("fangsong", serifFallback);
        EnvFont.setMapping("华文宋体", serifFallback);
        EnvFont.setMapping("华文楷体", serifFallback);
        EnvFont.setMapping("华文中宋", serifFallback);

        // Also set default font so unmapped font names fall back to CJK.
        EnvFont.setDefaultFont(sansFallback);

        log.info("Chinese font mappings registered: 宋体->{}, 楷体->{}",
            sansFallback.getFontName(), serifFallback.getFontName());
    }

    private Font findFont(String familyName) {
        for (Font f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()) {
            if (f.getFamily().equals(familyName) || f.getFontName().startsWith(familyName)) {
                return f;
            }
        }
        // Try case-insensitive
        for (Font f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()) {
            if (f.getFamily().equalsIgnoreCase(familyName)) {
                return f;
            }
        }
        return null;
    }
}
