package com.ofd.converter.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@Table("operation_log")
public class OperationLog implements Persistable<String> {
    @Id
    private String id;
    private String operationType;
    private String clientIp;
    private String fileId;
    private String taskId;
    private String targetFormat;
    private String status;   // SUCCESS | FAILED | TIMEOUT
    private Long durationMs;
    private String errorMessage;
    private String userAgent;
    private Long createdAt;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void markNotNew() {
        this.isNew = false;
    }
}
