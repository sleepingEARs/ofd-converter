package com.ofd.converter.model.dto;

import com.ofd.converter.model.OperationLog;
import java.util.List;

public record AdminLogsResponse(List<OperationLog> logs, long total, int page, int size) {}
