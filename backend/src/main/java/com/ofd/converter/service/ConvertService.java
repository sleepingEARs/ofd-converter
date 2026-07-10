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
    private final UploadRateLimiter rateLimiter;

    public ConvertService(FileService fileService, ValidationService validation,
                          TaskService taskService, ConvertPipeline pipeline, LogService logService,
                          @Qualifier("conversionExecutor") ExecutorService executor,
                          RetentionProperties props,
                          @Value("${conversion.timeout-minutes:5}") int timeoutMinutes,
                          UploadRateLimiter rateLimiter) {
        this.fileService = fileService;
        this.validation = validation;
        this.taskService = taskService;
        this.pipeline = pipeline;
        this.logService = logService;
        this.executor = executor;
        this.props = props;
        this.timeoutMinutes = timeoutMinutes;
        this.rateLimiter = rateLimiter;
    }

    public UploadResponse upload(MultipartFile file, String ip, String ua) {
        rateLimiter.check(ip);
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
        validation.requireFileId(req.fileId());
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
            req.options() == null ? null : req.options().toString(), warningFor(fmt));
        logService.record(OperationType.CONVERT, ip, req.fileId(), t.getId(), fmt.name(), "PENDING", 0, null, ua);

        // Run async with a per-task timeout. convert() returns immediately.
        // NOTE: ofdrw converters are not interruptible; orTimeout marks the task TIMEOUT but
        // the conversion thread continues until ofdrw completes or throws. A hung conversion
        // occupies a pool slot. Process-level isolation is a future enhancement.
        String fileId = req.fileId();
        CompletableFuture
            .runAsync(() -> runConversion(t, src, fmt, req.options()), executor)
            .orTimeout(timeoutMinutes, TimeUnit.MINUTES)
            .whenComplete((result, ex) -> {
                if (ex instanceof TimeoutException) {
                    taskService.markTimeout(t.getId());
                    logService.record(OperationType.CONVERT, null, fileId, t.getId(),
                        fmt.name(), "TIMEOUT", timeoutMinutes * 60_000L, "转换超时", null);
                } else if (ex != null) {
                    // Safety net: runConversion usually marks failed itself, but if the cause
                    // escaped before that (e.g. an Error from markProcessing), mark failed here.
                    // Guard against clobbering a terminal status already set.
                    try {
                        Task current = taskService.get(t.getId());
                        String s = current.getStatus();
                        if (!TaskStatus.TIMEOUT.name().equals(s)
                            && !TaskStatus.FAILED.name().equals(s)
                            && !TaskStatus.DONE.name().equals(s)) {
                            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                            taskService.markFailed(t.getId(), msg);
                            logService.record(OperationType.CONVERT, null, fileId, t.getId(),
                                fmt.name(), "FAILED", 0, msg, null);
                        }
                    } catch (Exception ignored) {
                    }
                }
                // success handled inside runConversion
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
        } catch (Throwable e) {
            Task current = taskService.get(t.getId());
            if (TaskStatus.TIMEOUT.name().equals(current.getStatus())) {
                // Error still needs to propagate so the CompletableFuture completes exceptionally.
                if (e instanceof Error) throw (Error) e;
                return;
            }
            String message = (e instanceof OutOfMemoryError) ? "内存不足" : e.getMessage();
            taskService.markFailed(t.getId(), message);
            logService.record(OperationType.CONVERT, null, t.getSourceFileId(), t.getId(),
                fmt.name(), "FAILED", System.currentTimeMillis() - start, message, null);
            try {
                fileService.deleteRecursively(fileService.createOutputDir(t.getId()));
            } catch (Exception ignored) {
            }
            // Propagate Errors (e.g. OOM) so the future completes exceptionally; the
            // whenComplete safety net won't double-mark because status is now FAILED.
            if (e instanceof Error) throw (Error) e;
        }
    }

    /** Lossy-conversion warning text, or null for lossless conversions. */
    private static String warningFor(ConvertFormat fmt) {
        return switch (fmt) {
            case DOCX -> "版式转 DOCX 为有损转换，排版可能变化，仅供参考";
            case MD -> "OFD 转 Markdown 为结构推断，复杂版面可能有损，仅供参考";
            default -> null;
        };
    }
}
