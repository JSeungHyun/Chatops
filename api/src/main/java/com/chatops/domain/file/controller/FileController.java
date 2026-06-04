package com.chatops.domain.file.controller;

import com.chatops.domain.file.dto.FileUploadResponse;
import com.chatops.domain.file.service.FileService;
import com.chatops.domain.file.service.FileService.FileUploadResult;
import com.chatops.domain.file.util.MagicByteValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_VIDEO_SIZE = 50 * 1024 * 1024; // 50MB

    private final FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("roomId") String roomId) {
        // Validation: empty file
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 비어있습니다");
        }

        // Validation: magic-byte check (must run before size limit to determine actual type)
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            int bytesRead = is.readNBytes(header, 0, 12);
            if (bytesRead < 4) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 헤더를 읽을 수 없습니다");
            }
            if (!MagicByteValidator.validate(header, contentType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "파일 내용이 선언된 형식과 일치하지 않습니다");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일을 읽을 수 없습니다");
        }

        // Validation: file size (video limit only if magic bytes confirmed video)
        boolean isVideo = contentType.startsWith("video/");
        long limit = isVideo ? MAX_VIDEO_SIZE : MAX_FILE_SIZE;
        if (file.getSize() > limit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 크기가 제한을 초과합니다");
        }

        FileUploadResult result = fileService.uploadFile(file, roomId);
        FileUploadResponse response = new FileUploadResponse(
            result.fileUrl(),
            result.fileName(),
            result.fileSize(),
            result.contentType()
        );
        return ResponseEntity.ok(response);
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
