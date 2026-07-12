package com.ofd.converter.controller;

import com.ofd.converter.engine.ConvertOptions;
import com.ofd.converter.engine.ConvertPipeline;
import com.ofd.converter.engine.ConvertResult;
import com.ofd.converter.interceptor.ClientIpInterceptor;
import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.model.OperationType;
import com.ofd.converter.model.SourceType;
import com.ofd.converter.model.Task;
import com.ofd.converter.model.TaskStatus;
import com.ofd.converter.model.dto.*;
import com.ofd.converter.service.ConvertService;
import com.ofd.converter.service.FileService;
import com.ofd.converter.service.LogService;
import com.ofd.converter.service.TaskService;
import com.ofd.converter.service.ValidationService;
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
    private final LogService logService;
    private final ConvertPipeline pipeline;
    private final ValidationService validation;

    public ConvertController(ConvertService convertService, TaskService taskService,
                             FileService fileService, LogService logService,
                             ConvertPipeline pipeline, ValidationService validation) {
        this.convertService = convertService;
        this.taskService = taskService;
        this.fileService = fileService;
        this.logService = logService;
        this.pipeline = pipeline;
        this.validation = validation;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/api/formats")
    public Map<String, List<String>> formats() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("ofd", List.of("pdf", "png", "jpg", "txt", "docx", "md"));
        m.put("pdf", List.of("ofd"));
        m.put("image", List.of("ofd"));
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
        return new TaskResponse(t.getId(), t.getStatus().toLowerCase(), url, t.getErrorMessage(), t.getWarning());
    }

    @GetMapping("/api/download/{taskId}")
    public ResponseEntity<FileSystemResource> download(@PathVariable String taskId, HttpServletRequest req) {
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
        logService.record(OperationType.DOWNLOAD, ClientIpInterceptor.extractIp(req), null,
            taskId, null, "SUCCESS", 0, null, req.getHeader("User-Agent"));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + t.getOutputFilename() + "\"")
            .body(new FileSystemResource(file));
    }

    /**
     * OFD preview via server-side rendering (ofd.js requires a paid license).
     * GET /api/preview/{file_id} -> {pages: [0,1,2,...], total: N}
     * GET /api/preview/{file_id}?page=N -> PNG image of page N
     */
    @GetMapping("/api/preview/{fileId}")
    public ResponseEntity<?> preview(@PathVariable String fileId,
                                     @RequestParam(value = "page", required = false) Integer page) throws Exception {
        validation.requireFileId(fileId);
        Path source = fileService.uploadFile(fileId);
        Path outDir = fileService.createOutputDir("preview-" + fileId);
        try {
            // Convert OFD -> PNG (all pages) synchronously.
            ConvertResult r = pipeline.run(SourceType.OFD, ConvertFormat.PNG, source, outDir,
                source.getFileName().toString(), new ConvertOptions(null, 150));
            // Ofd2Image produces a ZIP. Unzip to get individual page PNGs.
            Path pagesDir = outDir.resolve("pages");
            java.util.List<Path> pngs = new java.util.ArrayList<>();
            if (Files.isDirectory(pagesDir)) {
                try (var s = Files.list(pagesDir)) {
                    s.filter(p -> p.toString().endsWith(".png"))
                     .sorted()
                     .forEach(pngs::add);
                }
            }
            // If pages dir empty (fallback), extract from zip
            if (pngs.isEmpty()) {
                try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(r.outputFile()))) {
                    var entry = zis.getNextEntry();
                    while (entry != null) {
                        if (entry.getName().endsWith(".png")) {
                            Path p = outDir.resolve(entry.getName());
                            Files.copy(zis, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            pngs.add(p);
                        }
                        entry = zis.getNextEntry();
                    }
                }
            }

            if (page != null) {
                // Return a specific page as PNG image.
                if (page < 0 || page >= pngs.size()) {
                    throw new ApiException(com.ofd.converter.model.ErrorCode.INVALID_REQUEST, "页码超出范围", 400);
                }
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "image/png")
                    .body(new FileSystemResource(pngs.get(page)));
            }

            // Return page index list.
            java.util.List<Integer> indices = new java.util.ArrayList<>();
            for (int i = 0; i < pngs.size(); i++) indices.add(i);
            return ResponseEntity.ok(Map.of("pages", indices, "total", pngs.size()));
        } finally {
            // Clean up temp files after response (images are streamed by FileSystemResource).
            // Note: for image streaming, we can't delete before response is sent.
            // Spring will stream the FileSystemResource, so cleanup happens on next GC/retention.
            // For simplicity, leave temp files (retention scheduler cleans them).
        }
    }
}
