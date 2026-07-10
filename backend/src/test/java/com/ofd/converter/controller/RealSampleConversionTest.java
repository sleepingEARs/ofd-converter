package com.ofd.converter.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofd.converter.Fixtures;
import com.ofd.converter.engine.converters.Ofd2Docx;
import com.ofd.converter.engine.converters.Ofd2Markdown;
import com.ofd.converter.engine.extract.OfdTextBlockExtractor;
import com.ofd.converter.engine.structure.MdStructureInferrer;
import com.ofd.converter.engine.structure.OfdStructureInferrer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end: real/generated OFD samples -> DOCX + MD, via both direct converters and the
 * REST API (with warning field). Validates zero crashes + valid output + degradation.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RealSampleConversionTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;

    static Path tempDir;

    @org.junit.jupiter.api.BeforeAll
    static void setup() throws Exception {
        tempDir = Files.createTempDirectory("ofd-plan2-e2e");
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("file.data-dir", () -> tempDir.toString());
        r.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("test.db"));
    }

    private List<Path> samples() throws Exception {
        // Use real samples if present in src/test/resources/test-ofd, else generated fixtures.
        Path dir = Path.of("src/test/resources/test-ofd");
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (var s = Files.list(dir)) {
                s.filter(p -> p.toString().endsWith(".ofd")).forEach(files::add);
            }
        }
        if (files.size() < 5) {
            files.add(Fixtures.ofd(tempDir));
            files.add(Fixtures.ofdWithHeadings(tempDir));
            files.add(Fixtures.ofdWithList(tempDir));
        }
        return files;
    }

    @Test
    void directConversionNoCrash() throws Exception {
        Ofd2Docx docx = new Ofd2Docx(new OfdTextBlockExtractor(), new OfdStructureInferrer());
        Ofd2Markdown md = new Ofd2Markdown(new OfdTextBlockExtractor(), new MdStructureInferrer());
        for (Path sample : samples()) {
            var dr = docx.convert(sample, tempDir, sample.getFileName().toString(), null);
            byte[] dh = Files.readAllBytes(dr.outputFile());
            assertEquals(0x50, dh[0], sample + " DOCX must be ZIP");
            try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(dr.outputFile()))) {
                assertFalse(doc.getParagraphs().isEmpty(), sample + " DOCX has paragraphs");
            }
            var mr = md.convert(sample, tempDir, sample.getFileName().toString(), null);
            String mdtxt = Files.readString(mr.outputFile());
            assertFalse(mdtxt.isBlank(), sample + " MD non-empty");
        }
    }

    @Test
    void apiConvertReturnsWarningForMd() throws Exception {
        byte[] ofd = Files.readAllBytes(Fixtures.ofdWithHeadings(tempDir));
        MockMultipartFile mp = new MockMultipartFile("file", "h.ofd", "application/octet-stream", ofd);
        MvcResult up = mvc.perform(multipart("/api/upload").file(mp))
            .andExpect(status().isOk()).andReturn();
        String fileId = objectMapper.readTree(up.getResponse().getContentAsString()).get("file_id").asText();

        MvcResult cv = mvc.perform(post("/api/convert")
                .contentType("application/json")
                .content("{\"file_id\":\"" + fileId + "\",\"target_format\":\"md\"}"))
            .andExpect(status().isOk()).andReturn();
        String taskId = objectMapper.readTree(cv.getResponse().getContentAsString()).get("task_id").asText();

        String warning = null;
        for (int i = 0; i < 60; i++) {
            MvcResult tr = mvc.perform(get("/api/task/" + taskId)).andExpect(status().isOk()).andReturn();
            // Read response body as UTF-8 (default getContentAsString uses ISO-8859-1 -> mojibake).
            JsonNode j = objectMapper.readTree(tr.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            String status = j.get("status").asText();
            if ("done".equals(status) || "failed".equals(status) || "timeout".equals(status)) {
                warning = j.hasNonNull("warning") ? j.get("warning").asText() : null;
                break;
            }
            Thread.sleep(500);
        }
        assertEquals("OFD 转 Markdown 为结构推断，复杂版面可能有损，仅供参考", warning,
            "MD conversion must carry the lossy warning");
    }
}
