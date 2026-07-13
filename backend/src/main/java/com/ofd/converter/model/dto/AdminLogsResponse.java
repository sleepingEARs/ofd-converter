package com.ofd.converter.model.dto;

import java.util.List;

public record AdminLogsResponse(List<AdminLogEntry> logs, long total, int page, int size) {}