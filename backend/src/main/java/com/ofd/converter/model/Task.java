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
@Table("task")
public class Task implements Persistable<String> {
    @Id
    private String id;
    private String sourceFileId;
    private String sourceFilename;
    private String sourceType;
    private String targetFormat;
    private String status;
    private String optionsJson;
    private String outputPath;
    private String outputFilename;
    private Long outputSize;
    private String outputType;   // single | archive
    private String errorMessage;
    private Long downloadedAt;
    private Long createdAt;
    private Long updatedAt;
    private String warning;

    // Spring Data JDBC uses isNew() to decide INSERT vs UPDATE. Since we assign IDs
    // ourselves (UUID), the default (not-new if @Id set) would always UPDATE and fail.
    // An AfterConvertCallback flips this to false after loading from DB.
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
