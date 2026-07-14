package com.ofd.converter.service;

import com.ofd.converter.model.OperationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
class LogServiceIntegrationTest {

    static Path tempDir;

    @org.junit.jupiter.api.BeforeAll
    static void setup() throws Exception {
        tempDir = Files.createTempDirectory("ofd-log-test");
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("file.data-dir", () -> tempDir.toString());
        r.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("test.db"));
    }

    @Autowired
    LogService logService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void queryLogsReturnsStoredFilenameForUpload() throws Exception {
        String fileId = UUID.randomUUID().toString();
        logService.record(OperationType.UPLOAD, "1.2.3.4", fileId, null, "report.ofd",
            null, "SUCCESS", 0, null, "ua");

        // Wait for async log write.
        TimeUnit.MILLISECONDS.sleep(300);

        var resp = logService.queryLogs(1, 10, null, null, null, null, null);
        var log = resp.logs().stream()
            .filter(e -> fileId.equals(e.file_id()))
            .findFirst()
            .orElseThrow();
        assertEquals("report.ofd", log.filename());
    }

    @Test
    void queryLogsFallsBackToTaskSourceFilename() {
        String taskId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO task (id, source_file_id, source_filename, source_type, target_format, status, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            taskId, "f1", "from-task.ofd", "OFD", "PDF", "DONE", 1L, 1L);

        // Insert a log row without filename (simulates pre-migration record).
        String logId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO operation_log (id, operation_type, client_ip, file_id, task_id, status, created_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
            logId, OperationType.CONVERT.name(), "1.2.3.4", "f1", taskId, "SUCCESS", System.currentTimeMillis());

        var resp = logService.queryLogs(1, 10, null, null, null, null, null);
        var log = resp.logs().stream()
            .filter(e -> logId.equals(e.id()))
            .findFirst()
            .orElseThrow();
        assertEquals("from-task.ofd", log.filename());
    }

    @Test
    void queryLogsReturnsNullFilenameWhenNeitherStoredNorTaskExists() {
        String logId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO operation_log (id, operation_type, client_ip, file_id, status, created_at)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
            logId, OperationType.MCP_CALL.name(), "1.2.3.4", "f1", "SUCCESS", System.currentTimeMillis());

        var resp = logService.queryLogs(1, 10, null, null, null, null, null);
        var log = resp.logs().stream()
            .filter(e -> logId.equals(e.id()))
            .findFirst()
            .orElseThrow();
        assertNull(log.filename());
    }
}
