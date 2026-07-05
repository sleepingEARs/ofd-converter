package com.ofd.converter.service;

import com.ofd.converter.config.RetentionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileServiceTest {

    private FileService newFileService(Path dataDir) {
        RetentionProperties p = new RetentionProperties();
        p.setDataDir(dataDir.toString());
        return new FileService(p);
    }

    @Test
    void storeUpload(@TempDir Path tmp) throws Exception {
        FileService fs = newFileService(tmp);
        Path out = fs.storeUpload(new ByteArrayInputStream("hello".getBytes()), "f1", "../../x.ofd");
        assertEquals("x.ofd", out.getFileName().toString());
        assertTrue(Files.exists(out));
    }

    @Test
    void zipDir(@TempDir Path tmp) throws Exception {
        FileService fs = newFileService(tmp);
        Path dir = fs.createOutputDir("t1");
        Files.writeString(dir.resolve("0.png"), "x");
        Files.writeString(dir.resolve("1.png"), "y");
        Path zip = fs.zipDir(dir, "out_images.zip");
        assertTrue(Files.size(zip) > 0);
        assertEquals("out_images.zip", zip.getFileName().toString());
    }
}
