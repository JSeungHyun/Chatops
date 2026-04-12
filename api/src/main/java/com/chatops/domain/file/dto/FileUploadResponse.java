package com.chatops.domain.file.dto;

public record FileUploadResponse(
    String fileUrl,
    String fileName,
    long fileSize,
    String contentType
) {}
