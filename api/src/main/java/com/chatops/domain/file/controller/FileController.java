package com.chatops.domain.file.controller;

import com.chatops.domain.file.dto.FileUploadResponse;
import com.chatops.domain.file.service.FileService;
import com.chatops.domain.file.service.FileService.FileUploadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("roomId") String roomId) {
        FileUploadResult result = fileService.uploadFile(file, roomId);
        FileUploadResponse response = new FileUploadResponse(
            result.fileUrl(),
            result.fileName(),
            result.fileSize(),
            result.contentType()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/download/**")
    public ResponseEntity<Void> downloadFile(jakarta.servlet.http.HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        String objectKey = fullPath.substring(fullPath.indexOf("/files/download/") + "/files/download/".length());

        // Path traversal 방어
        if (objectKey.contains("..") || objectKey.startsWith("/")) {
            return ResponseEntity.badRequest().build();
        }

        String presignedUrl = fileService.getPresignedUrl(objectKey);
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(presignedUrl))
            .build();
    }
}
