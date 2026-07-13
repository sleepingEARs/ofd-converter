package com.ofd.converter.service;

import com.ofd.converter.model.*;
import com.ofd.converter.model.dto.AdminLogEntry;
import com.ofd.converter.model.dto.AdminLogsResponse;
import com.ofd.converter.repository.OperationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutorService;

@Service
public class LogService {
    private static final Logger log = LoggerFactory.getLogger(LogService.class);
    private final OperationLogRepository repo;
    private final JdbcTemplate jdbc;
    private final ExecutorService logExecutor;

    private static final RowMapper<OperationLog> ROW_MAPPER = (rs, rowNum) -> {
        OperationLog entry = new OperationLog();
        entry.setId(rs.getString("id"));
        entry.setOperationType(rs.getString("operation_type"));
        entry.setClientIp(rs.getString("client_ip"));
        entry.setFileId(rs.getString("file_id"));
        entry.setTaskId(rs.getString("task_id"));
        entry.setTargetFormat(rs.getString("target_format"));
        entry.setStatus(rs.getString("status"));
        entry.setDurationMs(rs.getLong("duration_ms"));
        entry.setErrorMessage(rs.getString("error_message"));
        entry.setUserAgent(rs.getString("user_agent"));
        entry.setCreatedAt(rs.getLong("created_at"));
        entry.markNotNew();
        return entry;
    };

    private static final RowMapper<AdminLogEntry> ADMIN_ROW_MAPPER = (rs, rowNum) ->
        new AdminLogEntry(
            rs.getString("id"),
            rs.getString("operation_type"),
            rs.getString("client_ip"),
            rs.getString("file_id"),
            rs.getString("task_id"),
            rs.getString("target_format"),
            rs.getString("status"),
            (Long) rs.getObject("duration_ms"),
            rs.getString("error_message"),
            rs.getString("user_agent"),
            rs.getLong("created_at"),
            rs.getString("filename")
        );

    public LogService(OperationLogRepository repo, JdbcTemplate jdbc,
                      @Qualifier("logExecutor") ExecutorService logExecutor) {
        this.repo = repo;
        this.jdbc = jdbc;
        this.logExecutor = logExecutor;
    }

    public void record(OperationType type, String ip, String fileId, String taskId,
                       String targetFormat, String status, long durationMs,
                       String error, String userAgent) {
        OperationLog entry = new OperationLog();
        entry.setId(UUID.randomUUID().toString());
        entry.setOperationType(type.name());
        entry.setClientIp(ip);
        entry.setFileId(fileId);
        entry.setTaskId(taskId);
        entry.setTargetFormat(targetFormat);
        entry.setStatus(status);
        entry.setDurationMs(durationMs);
        entry.setErrorMessage(error);
        entry.setUserAgent(userAgent);
        entry.setCreatedAt(System.currentTimeMillis());
        logExecutor.submit(() -> {
            try {
                repo.save(entry);
                log.info("log saved: type={} status={} fileId={} taskId={}",
                    type.name(), status, fileId, taskId);
            } catch (Exception e) {
                log.error("log write failed: type={} status={}", type.name(), status, e);
            }
        });
    }

    public AdminLogsResponse queryLogs(int page, int size, String operationType,
                                        String status, Long startDate, Long endDate,
                                        String search) {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (operationType != null && !operationType.isBlank()) {
            where.append(" AND operation_type = ?");
            params.add(operationType.toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            params.add(status.toUpperCase());
        }
        if (startDate != null) {
            where.append(" AND created_at >= ?");
            params.add(startDate);
        }
        if (endDate != null) {
            where.append(" AND created_at <= ?");
            params.add(endDate);
        }
        if (search != null && !search.isBlank()) {
            where.append(" AND (client_ip LIKE ? OR task_id LIKE ? OR task_id IN (SELECT id FROM task WHERE source_filename LIKE ?))");
            String like = "%" + search + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }

        String whereClause = where.toString();
        long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM operation_log WHERE 1=1" + whereClause,
            Long.class, params.toArray());

        int offset = (page - 1) * size;
        List<Object> listParams = new ArrayList<>(params);
        listParams.add(size);
        listParams.add(offset);
        List<AdminLogEntry> logs = jdbc.query(
            "SELECT id, operation_type, client_ip, file_id, task_id,"
                + " target_format, status, duration_ms, error_message,"
                + " user_agent, created_at, NULL AS filename"
                + " FROM operation_log WHERE 1=1" + whereClause
                + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
            ADMIN_ROW_MAPPER, listParams.toArray());

        // Batch-fill filenames from task table.
        List<String> taskIds = logs.stream()
            .map(AdminLogEntry::task_id)
            .filter(tid -> tid != null && !tid.isBlank())
            .distinct()
            .toList();
        if (!taskIds.isEmpty()) {
            String placeholders = taskIds.stream().map(t -> "?").reduce((a, b) -> a + "," + b).orElse("");
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, source_filename FROM task WHERE id IN (" + placeholders + ")",
                taskIds.toArray());
            Map<String, String> nameMap = new java.util.HashMap<>();
            for (var row : rows) {
                nameMap.put((String) row.get("id"), (String) row.get("source_filename"));
            }
            logs = logs.stream()
                .map(e -> new AdminLogEntry(e.id(), e.operation_type(), e.client_ip(),
                    e.file_id(), e.task_id(), e.target_format(), e.status(),
                    e.duration_ms(), e.error_message(), e.user_agent(),
                    e.created_at(), nameMap.get(e.task_id())))
                .toList();
        }

        return new AdminLogsResponse(logs, total, page, size);
    }
}
