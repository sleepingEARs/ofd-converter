package com.ofd.converter.model.dto;

import java.util.Map;

public record ConvertRequest(String fileId, String targetFormat, Map<String, Object> options) {}
