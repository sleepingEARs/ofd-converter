package com.ofd.converter.model.dto;

public record UploadResponse(String fileId, String filename, long size, String sourceType) {}
