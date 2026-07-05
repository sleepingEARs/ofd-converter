package com.ofd.converter.controller;

import com.ofd.converter.interceptor.ClientIpInterceptor;
import com.ofd.converter.model.Task;
import com.ofd.converter.model.TaskStatus;
import com.ofd.converter.model.dto.*;
import com.ofd.converter.service.ConvertService;
import com.ofd.converter.service.FileService;
import com.ofd.converter.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ConvertController {

    private final ConvertService convertService;
    private final TaskService taskService;
    private final FileService fileService;

    public ConvertController(ConvertService convertService, TaskService taskService, FileService fileService) {
        this.convertService = convertService;
        this.taskService = taskService;
        this.fileService = fileService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/api/formats")
    public Map<String, List<String>> formats() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("ofd", List.of("pdf", "png", "jpg", "docx", "txt", "md"));
        m.put("pdf", List.of("ofd", "png", "jpg"));
        m.put("image", List.of("ofd"));
        m.put("docx", List.of("ofd"));
        return m;
    }

    @PostMapping("/api/upload")
    public UploadResponse upload(@RequestParam("file") MultipartFile file, HttpServletRequest req) {
        return convertService.upload(file, ClientIpInterceptor.extractIp(req), req.getHeader("User-Agent"));
    }

    @PostMapping("/api/convert")
    public ConvertResponse convert(@RequestBody ConvertRequest body, HttpServletRequest req) {
        return convertService.convert(body, ClientIpInterceptor.extractIp(req), req.getHeader("User-Agent"));
    }

    @GetMapping("/api/task/{taskId}")
    public TaskResponse task(@PathVariable String taskId) {
        Task t = taskService.get(taskId);
        String url = TaskStatus.DONE.name().equals(t.getStatus()) ? "/api/download/" + t.getId() : null;
        return new TaskResponse(t.getId(), t.getStatus(), url, t.getErrorMessage());
    }

    @GetMapping("/api/download/{taskId}")
    public ResponseEntity<FileSystemResource> download(@PathVariable String taskId) {
        Task t = taskService.get(taskId);
        if (!TaskStatus.DONE.name().equals(t.getStatus())) {
            throw new ApiException(com.ofd.converter.model.ErrorCode.FILE_EXPIRED, "文件未就绪或已过期", 410);
        }
        Path file = Paths.get(t.getOutputPath());
        if (!Files.exists(file)) {
            throw new ApiException(com.ofd.converter.model.ErrorCode.FILE_EXPIRED, "文件已清理", 410);
        }
        if (t.getDownloadedAt() == null) {
            taskService.saveDownloadedAt(t.getId(), System.currentTimeMillis());
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + t.getOutputFilename() + "\"")
            .body(new FileSystemResource(file));
    }
}
