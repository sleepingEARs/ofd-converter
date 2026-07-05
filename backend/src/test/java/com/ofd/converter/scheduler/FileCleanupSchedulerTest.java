package com.ofd.converter.scheduler;

import com.ofd.converter.config.RetentionProperties;
import com.ofd.converter.repository.TaskRepository;
import com.ofd.converter.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FileCleanupSchedulerTest {

    @Test
    void deletesOldUploads(@TempDir Path tmp) throws Exception {
        RetentionProperties p = new RetentionProperties();
        p.setDataDir(tmp.toString());
        p.setRetentionHours(1);
        FileService fs = new FileService(p);
        TaskRepository taskRepo = mock(TaskRepository.class);
        FileCleanupScheduler sched = new FileCleanupScheduler(fs, taskRepo, p);

        Path oldDir = tmp.resolve("uploads/old");
        Files.createDirectories(oldDir);
        Files.writeString(oldDir.resolve("a.ofd"), "x");
        // set mtime to 2 hours ago
        Files.setLastModifiedTime(oldDir, FileTime.fromMillis(System.currentTimeMillis() - 2L * 3600_000L));

        sched.cleanup();

        assertFalse(Files.exists(oldDir));
        verify(taskRepo).deleteByCreatedAtBefore(anyLong());
    }
}
