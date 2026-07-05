package com.ofd.converter.engine;

import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.model.SourceType;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ConvertPipeline {
    private final Map<String, Converter> converters;

    public ConvertPipeline(List<Converter> all) {
        this.converters = new HashMap<>();
        for (Converter c : all) {
            if (c.source() != null && c.target() != null) {
                converters.put(key(c.source(), c.target()), c);
            }
        }
    }

    public ConvertResult run(SourceType src, ConvertFormat fmt, Path source, Path outputDir,
                             String sourceFilename, ConvertOptions opts) {
        Converter c = converters.get(key(src, fmt));
        if (c == null) {
            throw new IllegalArgumentException("不支持的转换: " + src + " → " + fmt);
        }
        try {
            return c.convert(source, outputDir, sourceFilename, opts);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String key(SourceType s, ConvertFormat f) {
        return s + "->" + f;
    }
}
