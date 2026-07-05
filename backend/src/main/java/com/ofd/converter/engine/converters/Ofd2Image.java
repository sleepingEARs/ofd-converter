package com.ofd.converter.engine.converters;

import com.ofd.converter.engine.*;
import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.model.SourceType;
import com.ofd.converter.service.FileService;
import org.ofdrw.converter.export.ImageExporter;

import java.nio.file.*;

/**
 * OFD → image (PNG/JPG). ofdrw's ImageExporter writes one image file per page into a
 * subdirectory; we zip that directory into a single {base}_images.zip for download.
 */
public class Ofd2Image implements Converter {
    private final FileService fileService;
    private final ConvertFormat format;

    public Ofd2Image(FileService fileService, ConvertFormat format) {
        this.fileService = fileService;
        this.format = format;
    }

    @Override
    public SourceType source() { return SourceType.OFD; }

    @Override
    public ConvertFormat target() { return format; }

    @Override
    public ConvertResult convert(Path source, Path outputDir, String sourceFilename, ConvertOptions opts) throws Exception {
        double ppm = opts != null && opts.dpi() != null ? opts.dpi() / 25.4 : 15d;
        Path imgDir = outputDir.resolve("pages");
        Files.createDirectories(imgDir);
        try (ImageExporter ex = new ImageExporter(source, imgDir, format.name(), ppm)) {
            ex.export();
        }
        String base = Ofd2Pdf.basename(sourceFilename, ".ofd");
        Path zip = fileService.zipDir(imgDir, base + "_images.zip");
        Path finalZip = outputDir.resolve(base + "_images.zip");
        if (!zip.equals(finalZip)) {
            Files.move(zip, finalZip, StandardCopyOption.REPLACE_EXISTING);
        }
        return new ConvertResult(finalZip, base + "_images.zip", Files.size(finalZip), "archive");
    }
}
