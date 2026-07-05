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

    public Task create(String fileId, String filename, SourceType src, ConvertFormat fmt, String optionsJson) {
        Task t = new Task();
        t.setId(UUID.randomUUID().toString());
        t.setSourceFileId(fileId);
        t.setSourceFilename(filename);
        t.setSourceType(src.name());
        t.setTargetFormat(fmt.name());
        t.setStatus(TaskStatus.PENDING.name());
        t.setOptionsJson(optionsJson);
        long now = System.currentTimeMillis();
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        return repo.save(t);
    }

    public Task get(String taskId) {
        return repo.findById(taskId)
            .orElseThrow(() -> new ApiException(ErrorCode.TASK_NOT_FOUND, "任务不存在", 404));
    }

    public void markProcessing(String taskId) {
        updateStatus(taskId, TaskStatus.PROCESSING);
    }

    public void markTimeout(String taskId) {
        updateStatus(taskId, TaskStatus.TIMEOUT);
    }

    public void markDone(String taskId, String outputPath, String outputFilename, Long size, String outputType) {
        Task t = get(taskId);
        t.setStatus(TaskStatus.DONE.name());
        t.setOutputPath(outputPath);
        t.setOutputFilename(outputFilename);
        t.setOutputSize(size);
        t.setOutputType(outputType);
        t.setUpdatedAt(System.currentTimeMillis());
        repo.save(t);
    }

    public void markFailed(String taskId, String error) {
        Task t = get(taskId);
        t.setStatus(TaskStatus.FAILED.name());
        t.setErrorMessage(error);
        t.setUpdatedAt(System.currentTimeMillis());
        repo.save(t);
    }

    private void updateStatus(String taskId, TaskStatus s) {
        Task t = get(taskId);
        t.setStatus(s.name());
        t.setUpdatedAt(System.currentTimeMillis());
        repo.save(t);
    }
}
