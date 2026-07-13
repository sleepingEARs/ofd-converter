package com.ofd.converter.model.dto;

/**
 * Flattened log entry with filename from JOINed task table.
 * All fields use snake_case for Jackson serialization (configured globally).
 */
public record AdminLogEntry(
    String id,
    String operation_type,
    String client_ip,
    String file_id,
    String task_id,
    String target_format,
    String status,
    Long duration_ms,
    String error_message,
    String user_agent,
    Long created_at,
    String filename
) {}