package com.ofd.converter.controller;

import com.ofd.converter.model.ErrorCode;
import com.ofd.converter.model.dto.AdminLogsResponse;
import com.ofd.converter.service.LogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
public class AdminController {

    private final LogService logService;

    public AdminController(LogService logService) {
        this.logService = logService;
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

    private void checkAuth(HttpServletRequest req) {
        String password = System.getenv("ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                "管理功能未配置（ADMIN_PASSWORD 环境变量未设置）", 503);
        }
        String token = req.getHeader("X-Admin-Token");
        // Constant-time comparison to prevent timing side-channel on the admin password.
        if (token == null || !MessageDigest.isEqual(
                password.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "密码错误", 401);
        }
    }
}
