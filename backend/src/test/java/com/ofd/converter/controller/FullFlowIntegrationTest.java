package com.ofd.converter.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofd.converter.Fixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FullFlowIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    static Path tempDir;

    @BeforeAll
    static void setup() throws Exception {
        tempDir = Files.createTempDirectory("ofd-integration-test");
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("file.data-dir", () -> tempDir.toString());
        // Dedicated file DB for this context: isolates from @DataJdbcTest's shared memdb1
        // while still giving cross-thread visibility (async conversion thread sees writes).
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("test.db"));
    }

    @Test
    void uploadConvertDownload() throws Exception {
        // 1. Upload an OFD fixture.
        byte[] ofd = Files.readAllBytes(Fixtures.ofd(tempDir));
        MockMultipartFile mp = new MockMultipartFile("file", "sample.ofd",
            "application/octet-stream", ofd);

        MvcResult uploadResult = mvc.perform(multipart("/api/upload").file(mp))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode uploadJson = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        String fileId = uploadJson.get("file_id").asText();

        // 2. Convert to PDF (snake_case request per spec §6).
        MvcResult convertResult = mvc.perform(post("/api/convert")
                .contentType("application/json")
                .content("{\"file_id\":\"" + fileId + "\",\"target_format\":\"pdf\"}"))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode convertJson = objectMapper.readTree(convertResult.getResponse().getContentAsString());
        String taskId = convertJson.get("task_id").asText();

        // 3. Poll task status until done (or failure).
        boolean done = false;
        String lastStatus = null;
        for (int i = 0; i < 60; i++) {
            MvcResult taskResult = mvc.perform(get("/api/task/" + taskId))
                .andExpect(status().isOk())
                .andReturn();
            JsonNode taskJson = objectMapper.readTree(taskResult.getResponse().getContentAsString());
            lastStatus = taskJson.get("status").asText();
            if ("done".equals(lastStatus)) {
                done = true;
                break;
            }
            if ("failed".equals(lastStatus) || "timeout".equals(lastStatus)) {
                break;
            }
            Thread.sleep(500);
        }
        assertTrue(done, "task should reach done, last status was: " + lastStatus);

        // 4. Download the result; expect sample.pdf as the attachment filename.
        mvc.perform(get("/api/download/" + taskId))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.containsString("sample.pdf")));
    }
}
