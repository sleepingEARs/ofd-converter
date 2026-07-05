package com.ofd.converter.engine;

import java.nio.file.Path;

public record ConvertResult(Path outputFile, String outputFilename, long size, String outputType) {}
