package com.ofd.converter.engine.extract;

import org.ofdrw.core.basicStructure.pageObj.Page;
import org.ofdrw.core.basicStructure.pageObj.layer.PageBlockType;
import org.ofdrw.core.basicStructure.pageObj.layer.block.TextObject;
import org.ofdrw.core.basicType.ST_Box;
import org.ofdrw.core.basicType.ST_RefID;
import org.ofdrw.core.text.TextCode;
import org.ofdrw.reader.OFDReader;
import org.ofdrw.reader.PageInfo;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts text blocks (with geometry + font size) from an OFD via ofdrw-reader.
 * Does NO structure inference - just raw blocks. Shared by DOCX and Markdown paths.
 */
@Component
public class OfdTextBlockExtractor {

    public List<TextBlock> extract(Path ofd) throws Exception {
        List<TextBlock> blocks = new ArrayList<>();
        try (OFDReader reader = new OFDReader(ofd)) {
            int pageIndex = 0;
            for (PageInfo info : reader.getPageList()) {
                Page page = info.getObj();
                if (page == null || page.getContent() == null) {
                    pageIndex++;
                    continue;
                }
                for (var layer : page.getContent().getLayers()) {
                    for (PageBlockType block : layer.getPageBlocks()) {
                        if (block instanceof TextObject to) {
                            TextBlock tb = toTextBlock(to, pageIndex);
                            if (tb != null) blocks.add(tb);
                        }
                    }
                }
                pageIndex++;
            }
        }
        return blocks;
    }

    private TextBlock toTextBlock(TextObject to, int pageIndex) {
        StringBuilder sb = new StringBuilder();
        for (TextCode tc : to.getTextCodes()) {
            if (tc.getContent() != null) sb.append(tc.getContent());
        }
        String text = sb.toString();
        if (text.isBlank()) return null;

        ST_Box box = to.getBoundary();
        double x = 0, y = 0, w = 0, h = 0;
        if (box != null) {
            x = box.getTopLeftX() == null ? 0 : box.getTopLeftX();
            y = box.getTopLeftY() == null ? 0 : box.getTopLeftY();
            w = box.getWidth() == null ? 0 : box.getWidth();
            h = box.getHeight() == null ? 0 : box.getHeight();
        }
        Double fontSize = to.getSize();
        ST_RefID font = to.getFont();
        String fontRefId = font == null ? null : font.toString();
        return new TextBlock(pageIndex, x, y, w, h, fontSize, fontRefId, text);
    }
}
