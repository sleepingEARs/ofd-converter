package com.ofd.converter.mcp.tools;

import com.ofd.converter.engine.ConvertOptions;
import com.ofd.converter.engine.ConvertPipeline;
import com.ofd.converter.engine.ConvertResult;
import com.ofd.converter.mcp.McpErrors;
import com.ofd.converter.mcp.McpSession;
import com.ofd.converter.mcp.McpTool;
import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.model.SourceType;
import com.ofd.converter.service.FileService;
import com.ofd.converter.service.ValidationService;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ExtractOfdMarkdownTool implements McpTool {

    private static final int TIMEOUT_SECONDS = 30;

    private final FileService fileService;
    private final ConvertPipeline pipeline;
    private final ValidationService validation;

    public ExtractOfdMarkdownTool(FileService fileService, ConvertPipeline pipeline, ValidationService validation) {
        this.fileService = fileService;
        this.pipeline = pipeline;
        this.validation = validation;
    }

    @Override
    public String name() { return "extract_ofd_markdown"; }

    @Override
    public String description() { return "同步提取 OFD 为 Markdown（供 AI Agent 消费），直接返回内容。"; }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "file_id", Map.of("type", "string"),
                "pages", Map.of("type", "string")
            ),
            "required", java.util.List.of("file_id")
        );
    }

    @Override
    public Object execute(Map<String, Object> args, McpSession session) throws Exception {
        String fileId = str(args.get("file_id"));
        if (fileId == null) throw new McpErrors.McpException(McpErrors.INVALID_PARAMS, "缺少 file_id");
        try {
            validation.requireFileId(fileId);  // UUID check — prevents path traversal (../../etc)
        } catch (com.ofd.converter.controller.ApiException e) {
            throw new McpErrors.McpException(McpErrors.INVALID_PARAMS, "无效的 file_id");
        }
        var source = fileService.uploadFile(fileId);
        var outDir = fileService.createOutputDir("mcp-md-" + fileId);
        try {
            ConvertResult r = CompletableFuture
                .supplyAsync(() -> pipeline.run(SourceType.OFD, ConvertFormat.MD, source, outDir,
                    source.getFileName().toString(), ConvertOptions.from(null)))
                .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .join();
            String md = Files.readString(r.outputFile());
            return Map.of("markdown", md);
        } catch (java.util.concurrent.CompletionException e) {
            if (e.getCause() instanceof TimeoutException) {
                throw new McpErrors.McpException(McpErrors.INTERNAL_ERROR, "提取超时（" + TIMEOUT_SECONDS + " 秒）");
            }
            throw e.getCause() instanceof Exception ce ? ce : e;
        } finally {
            fileService.deleteRecursively(outDir);
        }
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
}
