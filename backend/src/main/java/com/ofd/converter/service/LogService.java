package com.ofd.converter.service;

import com.ofd.converter.model.*;
import com.ofd.converter.repository.OperationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Service
public class LogService {
    private static final Logger log = LoggerFactory.getLogger(LogService.class);
    private final OperationLogRepository repo;
    private final ExecutorService logExecutor;

    public LogService(OperationLogRepository repo, @Qualifier("logExecutor") ExecutorService logExecutor) {
        this.repo = repo;
        this.logExecutor = logExecutor;
    }

    public void record(OperationType type, String ip, String fileId, String taskId,
                       String targetFormat, String status, long durationMs,
                       String error, String userAgent) {
        OperationLog entry = new OperationLog();
        entry.setId(UUID.randomUUID().toString());
        entry.setOperationType(type.name());
        entry.setClientIp(ip);
        entry.setFileId(fileId);
        entry.setTaskId(taskId);
        entry.setTargetFormat(targetFormat);
        entry.setStatus(status);
        entry.setDurationMs(durationMs);
        entry.setErrorMessage(error);
        entry.setUserAgent(userAgent);
        entry.setCreatedAt(System.currentTimeMillis());
        logExecutor.submit(() -> {
            try {
                repo.save(entry);
            } catch (Exception e) {
                log.warn("log write failed", e);
            }
        });
    }
}
