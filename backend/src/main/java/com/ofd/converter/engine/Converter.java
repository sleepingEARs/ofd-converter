package com.ofd.converter.engine;

import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.model.SourceType;

import java.nio.file.Path;

public interface Converter {
    ConvertResult convert(Path source, Path outputDir, String sourceFilename, ConvertOptions opts) throws Exception;

    /** Source type this converter reads. Null if not a routed converter. */
    default SourceType source() { return null; }

    /** Target format this converter produces. Null if not a routed converter. */
    default ConvertFormat target() { return null; }
}
