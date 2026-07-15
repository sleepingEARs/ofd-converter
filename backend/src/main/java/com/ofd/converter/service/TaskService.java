package com.ofd.converter.service;

import com.ofd.converter.controller.ApiException;
import com.ofd.converter.model.*;
import com.ofd.converter.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TaskService {
    private final TaskRepository repo;

    public TaskService(TaskRepository repo) {
        this.repo = repo;
    }

    public Task create(String fileId, String filename, SourceType src, ConvertFormat fmt, String optionsJson, String warning) {
        Task t = new Task();
        t.setId(UUID.randomUUID().toString());
        t.setSourceFileId(fileId);
        t.setSourceFilename(filename);
        t.setSourceType(src.name());
        t.setTargetFormat(fmt.name());
        t.setStatus(TaskStatus.PENDING.name());
        t.setOptionsJson(optionsJson);
        t.setWarning(warning);
        long now = System.currentTimeMillis();
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        return repo.save(t);
    }

    public Task get(String taskId) {
        return repo.findById(taskId)
            .orElseThrow(() -> new ApiException(ErrorCode.TASK_NOT_FOUND, "任务不存在", 404));
    }

    private static final java.util.Set<String> TERMINAL =
        java.util.Set.of(TaskStatus.DONE.name(), TaskStatus.FAILED.name(), TaskStatus.TIMEOUT.name());

    public synchronized void markProcessing(String taskId) {
        updateStatus(taskId, TaskStatus.PROCESSING, TERMINAL);
    }

    public synchronized void markTimeout(String taskId) {
        // TIMEOUT must not overwrite DONE (conversion finished right as the timeout fired).
        updateStatus(taskId, TaskStatus.TIMEOUT,
            java.util.Set.of(TaskStatus.DONE.name(), TaskStatus.TIMEOUT.name()));
    }

    public synchronized void markDone(String taskId, String outputPath, String outputFilename, Long size, String outputType) {
        Task t = get(taskId);
        // Don't clobber a terminal status set by a concurrent handler (e.g. TIMEOUT).
        if (TERMINAL.contains(t.getStatus()) && !TaskStatus.DONE.name().equals(t.getStatus())) return;
        t.setStatus(TaskStatus.DONE.name());
        t.setOutputPath(outputPath);
        t.setOutputFilename(outputFilename);
        t.setOutputSize(size);
        t.setOutputType(outputType);
        t.setUpdatedAt(System.currentTimeMillis());
        repo.save(t);
    }

    public synchronized void markFailed(String taskId, String error) {
        Task t = get(taskId);
        // Don't overwrite DONE/TIMEOUT with FAILED.
        if (TaskStatus.DONE.name().equals(t.getStatus()) || TaskStatus.TIMEOUT.name().equals(t.getStatus())) return;
        t.setStatus(TaskStatus.FAILED.name());
        t.setErrorMessage(error);
        t.setUpdatedAt(System.currentTimeMillis());
        repo.save(t);
    }

    public void saveDownloadedAt(String taskId, long ts) {
        Task t = get(taskId);
        t.setDownloadedAt(ts);
        t.setUpdatedAt(System.currentTimeMillis());
        repo.save(t);
    }

    private void updateStatus(String taskId, TaskStatus s, java.util.Set<String> doNotOverride) {
        Task t = get(taskId);
        if (doNotOverride.contains(t.getStatus())) return;
        t.setStatus(s.name());
        t.setUpdatedAt(System.currentTimeMillis());
        repo.save(t);
    }
}
