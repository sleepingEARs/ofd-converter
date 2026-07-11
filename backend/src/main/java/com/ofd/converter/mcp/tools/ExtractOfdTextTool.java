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
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.Map;

@Component
public class ExtractOfdTextTool implements McpTool {

    private final FileService fileService;
    private final ConvertPipeline pipeline;

    public ExtractOfdTextTool(FileService fileService, ConvertPipeline pipeline) {
        this.fileService = fileService;
        this.pipeline = pipeline;
    }

    @Override
    public String name() { return "extract_ofd_text"; }

    @Override
    public String description() { return "同步提取 OFD 文本内容，直接返回文本（无需下载）。"; }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "file_id", Map.of("type", "string"),
                "pages", Map.of("type", "string", "description", "可选页码范围，如 1-5")
            ),
            "required", java.util.List.of("file_id")
        );
    }

    @Override
    public Object execute(Map<String, Object> args, McpSession session) throws Exception {
        String fileId = str(args.get("file_id"));
        if (fileId == null) throw new McpErrors.McpException(McpErrors.INVALID_PARAMS, "缺少 file_id");
        var source = fileService.uploadFile(fileId);
        var outDir = fileService.createOutputDir("mcp-text-" + fileId);
        try {
            ConvertResult r = pipeline.run(SourceType.OFD, ConvertFormat.TXT, source, outDir,
                source.getFileName().toString(), ConvertOptions.from(null));
            String text = Files.readString(r.outputFile());
            return Map.of("text", text);
        } finally {
            fileService.deleteRecursively(outDir);
        }
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
}