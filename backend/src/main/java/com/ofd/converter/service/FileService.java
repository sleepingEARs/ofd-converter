package com.ofd.converter.service;

import com.ofd.converter.config.RetentionProperties;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.stream.Stream;

@Service
public class FileService {
    private final Path dataDir;

    public FileService(RetentionProperties props) {
        this.dataDir = Paths.get(props.getDataDir());
    }

    public Path storeUpload(InputStream in, String fileId, String filename) throws IOException {
        Path dir = dataDir.resolve("uploads/" + fileId);
        Files.createDirectories(dir);
        String normalized = filename.replace("\\", "/");
        String safe = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\\\/]", "").replaceAll("\\p{Cntrl}", "").trim();
        Path target = dir.resolve(safe.isBlank() ? "file" : safe);
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public Path createOutputDir(String taskId) throws IOException {
        Path dir = dataDir.resolve("outputs/" + taskId);
        Files.createDirectories(dir);
        return dir;
    }

    /** Returns the single uploaded file for a given file_id (each upload gets its own dir). */
    public Path uploadFile(String fileId) {
        Path dir = dataDir.resolve("uploads/" + fileId);
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isRegularFile).findFirst()
                .orElseThrow(() -> new java.nio.file.NoSuchFileException("upload not found: " + fileId));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path zipDir(Path dir, String zipName) throws IOException {
        Path zip = dir.resolve(zipName);
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(zip.toFile());
             Stream<Path> files = Files.list(dir)) {
            files.filter(f -> !f.equals(zip) && Files.isRegularFile(f))
                 .forEach(f -> {
                     try (InputStream fin = Files.newInputStream(f)) {
                         ZipArchiveEntry e = new ZipArchiveEntry(f.getFileName().toString());
                         zos.putArchiveEntry(e);
                         fin.transferTo(zos);
                         zos.closeArchiveEntry();
                     } catch (IOException e) {
                         throw new UncheckedIOException(e);
                     }
                 });
            zos.finish();
        }
        return zip;
    }

    public boolean diskOk() {
        try {
            double usable = dataDir.toFile().getUsableSpace();
            double total = dataDir.toFile().getTotalSpace();
            if (total == 0) return true;
            double usedRatio = 1.0 - (usable / total);
            return usedRatio < 0.95;
        } catch (Exception e) {
            return true;
        }
    }

    public void deleteRecursively(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> walk = Files.walk(p)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(f -> {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
