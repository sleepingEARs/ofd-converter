package com.ofd.converter.service;

import com.ofd.converter.config.RetentionProperties;
import com.ofd.converter.controller.ApiException;
import com.ofd.converter.engine.ConvertOptions;
import com.ofd.converter.engine.ConvertPipeline;
import com.ofd.converter.engine.ConvertResult;
import com.ofd.converter.model.*;
import com.ofd.converter.model.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Orchestrates upload + conversion. convert() returns immediately with a PENDING task_id;
 * the conversion runs on the conversionExecutor with a per-task timeout. The frontend polls
 * /api/task/{id} for status.
 */
@Service
public class ConvertService {
    private static final Logger log = LoggerFactory.getLogger(ConvertService.class);

    private final FileService fileService;
    private final ValidationService validation;
    private final TaskService taskService;
    private final ConvertPipeline pipeline;
    private final LogService logService;
    private final ExecutorService executor;
    private final RetentionProperties props;
    private final int timeoutMinutes;

    public ConvertService(FileService fileService, ValidationService validation,
                          TaskService taskService, ConvertPipeline pipeline, LogService logService,
                          @Qualifier("conversionExecutor") ExecutorService executor,
                          RetentionProperties props,
                          @Value("${conversion.timeout-minutes:5}") int timeoutMinutes) {
        this.fileService = fileService;
        this.validation = validation;
        this.taskService = taskService;
        this.pipeline = pipeline;
        this.logService = logService;
        this.executor = executor;
        this.props = props;
        this.timeoutMinutes = timeoutMinutes;
    }

    public UploadResponse upload(MultipartFile file, String ip, String ua) {
        validation.validateSize(file.getSize());
        if (!fileService.diskOk()) {
            throw new ApiException(ErrorCode.STORAGE_FULL, "磁盘空间不足", 503);
        }
        byte[] head;
        try {
            head = file.getInputStream().readNBytes(8);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "读取文件失败", 400);
        }
        String originalName = file.getOriginalFilename();
        SourceType src = validation.detect(head, originalName);
        String safeName = validation.sanitizeFilename(originalName);
        String fileId = UUID.randomUUID().toString();
        try {
            fileService.storeUpload(file.getInputStream(), fileId, safeName);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "存储失败", 500);
        }
        logService.record(OperationType.UPLOAD, ip, fileId, null, null, "SUCCESS", 0, null, ua);
        return new UploadResponse(fileId, safeName, file.getSize(), src.name());
    }

    public ConvertResponse convert(ConvertRequest req, String ip, String ua) {
        Path source = fileService.uploadFile(req.fileId());
        String filename = source.getFileName().toString();
        byte[] head;
        try (var is = Files.newInputStream(source)) {
            head = is.readNBytes(8);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "源文件读取失败", 400);
        }
        SourceType src = validation.detect(head, filename);
        ConvertFormat fmt;
        try {
            fmt = ConvertFormat.valueOf(req.targetFormat().toUpperCase());
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "目标格式不支持", 400);
        }

        Task t = taskService.create(req.fileId(), filename, src, fmt,
            req.options() == null ? null : req.options().toString());
        logService.record(OperationType.CONVERT, ip, req.fileId(), t.getId(), fmt.name(), "PENDING", 0, null, ua);

        // Run async with a per-task timeout. convert() returns immediately.
        CompletableFuture
            .runAsync(() -> runConversion(t, src, fmt, req.options()), executor)
            .orTimeout(timeoutMinutes, TimeUnit.MINUTES)
            .whenComplete((result, ex) -> {
                if (ex instanceof TimeoutException) {
                    taskService.markTimeout(t.getId());
                    logService.record(OperationType.CONVERT, null, req.fileId(), t.getId(),
                        fmt.name(), "TIMEOUT", timeoutMinutes * 60_000L, "转换超时", null);
                }
                // success / failure handled inside runConversion
            });

        return new ConvertResponse(t.getId(), t.getStatus().toLowerCase());
    }

    private void runConversion(Task t, SourceType src, ConvertFormat fmt, Map<String, Object> options) {
        long start = System.currentTimeMillis();
        taskService.markProcessing(t.getId());
        try {
            Path source = fileService.uploadFile(t.getSourceFileId());
            Path outDir = fileService.createOutputDir(t.getId());
            ConvertOptions opts = ConvertOptions.from(options);
            ConvertResult r = pipeline.run(src, fmt, source, outDir, t.getSourceFilename(), opts);
            // Don't overwrite a TIMEOUT status set by the timeout handler.
            Task current = taskService.get(t.getId());
            if (TaskStatus.TIMEOUT.name().equals(current.getStatus())) {
                return;
            }
            taskService.markDone(t.getId(), r.outputFile().toString(), r.outputFilename(), r.size(), r.outputType());
            logService.record(OperationType.CONVERT, null, t.getSourceFileId(), t.getId(),
                fmt.name(), "SUCCESS", System.currentTimeMillis() - start, null, null);
        } catch (Exception e) {
            Task current = taskService.get(t.getId());
            if (TaskStatus.TIMEOUT.name().equals(current.getStatus())) {
                return;
            }
            taskService.markFailed(t.getId(), e.getMessage());
            logService.record(OperationType.CONVERT, null, t.getSourceFileId(), t.getId(),
                fmt.name(), "FAILED", System.currentTimeMillis() - start, e.getMessage(), null);
            try {
                fileService.deleteRecursively(fileService.createOutputDir(t.getId()));
            } catch (Exception ignored) {
            }
        }
    }
}
