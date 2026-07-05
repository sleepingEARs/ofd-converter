package com.ofd.converter.scheduler;

import com.ofd.converter.config.RetentionProperties;
import com.ofd.converter.repository.TaskRepository;
import com.ofd.converter.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@Component
public class FileCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(FileCleanupScheduler.class);
    private final FileService fileService;
    private final TaskRepository taskRepo;
    private final RetentionProperties props;

    public FileCleanupScheduler(FileService fileService, TaskRepository taskRepo, RetentionProperties props) {
        this.fileService = fileService;
        this.taskRepo = taskRepo;
        this.props = props;
    }

    @Scheduled(fixedRate = 3600_000L)
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - (long) props.getRetentionHours() * 3600_000L;
        Path root = Paths.get(props.getDataDir());
        for (String sub : List.of("uploads", "outputs")) {
            Path dir = root.resolve(sub);
            if (!Files.exists(dir)) continue;
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(Files::isDirectory).forEach(d -> {
                    try {
                        if (Files.getLastModifiedTime(d).toMillis() < cutoff) {
                            fileService.deleteRecursively(d);
                        }
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException e) {
                log.warn("cleanup list failed for {}", dir, e);
            }
        }
        taskRepo.deleteByCreatedAtBefore(cutoff);
    }
}
