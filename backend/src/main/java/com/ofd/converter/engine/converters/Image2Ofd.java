package com.ofd.converter.engine.converters;

import com.ofd.converter.engine.*;
import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.model.SourceType;
import org.ofdrw.converter.ofdconverter.ImageConverter;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class Image2Ofd implements Converter {
    @Override
    public SourceType source() { return SourceType.IMAGE; }

    @Override
    public ConvertFormat target() { return ConvertFormat.OFD; }

    @Override
    public ConvertResult convert(Path source, Path outputDir, String sourceFilename, ConvertOptions opts) throws Exception {
        String base = stripImageExt(sourceFilename);
        Path out = outputDir.resolve(base + ".ofd");
        try (ImageConverter c = new ImageConverter(out)) {
            c.convert(source);
        }
        return new ConvertResult(out, base + ".ofd", Files.size(out), "single");
    }

    private static String stripImageExt(String filename) {
        String n = filename == null ? "file" : filename.replace('\\', '/');
        int slash = n.lastIndexOf('/');
        if (slash >= 0) n = n.substring(slash + 1);
        for (String ext : new String[]{".png", ".jpg", ".jpeg", ".bmp"}) {
            if (n.toLowerCase().endsWith(ext)) {
                n = n.substring(0, n.length() - ext.length());
                break;
            }
        }
        return n.isBlank() ? "file" : n;
    }
}
