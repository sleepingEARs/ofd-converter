package com.ofd.converter.controller;

import com.ofd.converter.model.ErrorCode;
import com.ofd.converter.model.dto.AdminLogsResponse;
import com.ofd.converter.service.LogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class AdminController {

    private final LogService logService;
    private final JdbcTemplate jdbc;

    public AdminController(LogService logService, JdbcTemplate jdbc) {
        this.logService = logService;
        this.jdbc = jdbc;
    }

    @GetMapping("/api/admin/logs")
    public AdminLogsResponse logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String operation_type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long start_date,
            @RequestParam(required = false) Long end_date,
            @RequestParam(required = false) String search,
            HttpServletRequest req) {
        checkAuth(req);
        return logService.queryLogs(page, size, operation_type, status,
            start_date, end_date, search);
    }

    @GetMapping("/api/admin/debug")
    public Map<String, Object> debug(HttpServletRequest req) {
        checkAuth(req);
        long logCount = jdbc.queryForObject("SELECT COUNT(*) FROM operation_log", Long.class);
        long taskCount = jdbc.queryForObject("SELECT COUNT(*) FROM task", Long.class);
        return Map.of("operation_log_count", logCount, "task_count", taskCount);
    }

    private void checkAuth(HttpServletRequest req) {
        String password = System.getenv("ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                "管理功能未配置（ADMIN_PASSWORD 环境变量未设置）", 503);
        }
        String token = req.getHeader("X-Admin-Token");
        if (!password.equals(token)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "密码错误", 401);
        }
    }
}
