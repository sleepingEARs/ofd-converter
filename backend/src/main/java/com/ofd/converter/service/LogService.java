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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
            } catch (Exception e) {
                log.warn("log write failed", e);
            }
        });
    }

    public AdminLogsResponse queryLogs(int page, int size, String operationType,
                                        String status, Long startDate, Long endDate,
                                        String search) {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (operationType != null && !operationType.isBlank()) {
            where.append(" AND o.operation_type = ?");
            params.add(operationType.toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND o.status = ?");
            params.add(status.toUpperCase());
        }
        if (startDate != null) {
            where.append(" AND o.created_at >= ?");
            params.add(startDate);
        }
        if (endDate != null) {
            where.append(" AND o.created_at <= ?");
            params.add(endDate);
        }
        if (search != null && !search.isBlank()) {
            where.append(" AND (t.source_filename LIKE ? OR o.client_ip LIKE ? OR o.task_id LIKE ?)");
            String like = "%" + search + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }

        String whereClause = where.toString();
        long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM operation_log o LEFT JOIN task t ON o.task_id = t.id WHERE 1=1" + whereClause,
            Long.class, params.toArray());

        int offset = (page - 1) * size;
        List<Object> listParams = new ArrayList<>(params);
        listParams.add(size);
        listParams.add(offset);
        List<AdminLogEntry> logs = jdbc.query(
            "SELECT o.*, t.source_filename AS filename FROM operation_log o LEFT JOIN task t ON o.task_id = t.id WHERE 1=1"
                + whereClause + " ORDER BY o.created_at DESC LIMIT ? OFFSET ?",
            ADMIN_ROW_MAPPER, listParams.toArray());

        return new AdminLogsResponse(logs, total, page, size);
    }
}
