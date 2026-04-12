package com.chatops.domain.file.service;

import com.chatops.global.config.MinioConfig;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int THUMBNAIL_SIZE = 200;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    private static final Set<String> ALLOWED_FILE_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp",
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain",
        "application/zip",
        "application/x-zip-compressed"
    );

    public FileUploadResult uploadFile(MultipartFile file, String roomId) {
        validateFile(file);

        String originalFilename = file.getOriginalFilename() != null
            ? file.getOriginalFilename() : "unknown";
        String objectKey = "rooms/" + roomId + "/" + UUID.randomUUID() + "/" + originalFilename;
        String contentType = file.getContentType() != null
            ? file.getContentType() : "application/octet-stream";

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(minioConfig.getBucket())
                .object(objectKey)
                .stream(inputStream, file.getSize(), -1)
                .contentType(contentType)
                .build());

            String fileUrl = "/files/download/" + objectKey;

            log.info("File uploaded: key={}, size={}, type={}", objectKey, file.getSize(), contentType);

            return new FileUploadResult(fileUrl, originalFilename, file.getSize(), contentType);
        } catch (Exception e) {
            log.error("File upload failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다");
        }
    }

    public String getPresignedUrl(String objectKey) {
        try {
            String presignedUrl = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(minioConfig.getBucket())
                .object(objectKey)
                .expiry(1, TimeUnit.HOURS)
                .build());

            // Rewrite internal Docker URL to Nginx-proxied path
            // http://minio:9000/chatops-files/key?params → /minio/chatops-files/key?params
            return presignedUrl.replaceFirst("http://[^/]+:\\d+", "/minio");
        } catch (Exception e) {
            log.error("Failed to generate presigned URL: key={}", objectKey);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 URL 생성에 실패했습니다");
        }
    }

    public InputStream getFile(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioConfig.getBucket())
                .object(objectKey)
                .build());
        } catch (Exception e) {
            log.error("Failed to get file: key={}", objectKey);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다");
        }
    }

    public void deleteFile(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(minioConfig.getBucket())
                .object(objectKey)
                .build());
            log.info("File deleted: key={}", objectKey);
        } catch (Exception e) {
            log.error("Failed to delete file: key={}", objectKey);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 삭제에 실패했습니다");
        }
    }

    public String generateThumbnail(String objectKey) {
        try (InputStream inputStream = getFile(objectKey)) {
            BufferedImage original = ImageIO.read(inputStream);
            if (original == null) {
                log.warn("Cannot read image for thumbnail: key={}", objectKey);
                return null;
            }

            int width = original.getWidth();
            int height = original.getHeight();
            double ratio = Math.min((double) THUMBNAIL_SIZE / width, (double) THUMBNAIL_SIZE / height);
            int newWidth = (int) (width * ratio);
            int newHeight = (int) (height * ratio);

            BufferedImage thumbnail = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumbnail.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(original, 0, 0, newWidth, newHeight, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(thumbnail, "jpeg", baos);
            byte[] thumbBytes = baos.toByteArray();

            String thumbKey = objectKey + "-thumb.jpg";
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(minioConfig.getBucket())
                .object(thumbKey)
                .stream(new ByteArrayInputStream(thumbBytes), thumbBytes.length, -1)
                .contentType("image/jpeg")
                .build());

            log.info("Thumbnail generated: key={}", thumbKey);
            return "/files/download/" + thumbKey;
        } catch (Exception e) {
            log.error("Thumbnail generation failed: key={}, error={}", objectKey, e.getMessage());
            return null;
        }
    }

    public boolean isImageType(String contentType) {
        return contentType != null && ALLOWED_IMAGE_TYPES.contains(contentType);
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 비어있습니다");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 크기는 10MB를 초과할 수 없습니다");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_FILE_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "허용되지 않는 파일 형식입니다. 허용: 이미지(jpeg/png/gif/webp), PDF, DOC, TXT, ZIP");
        }
    }

    public record FileUploadResult(String fileUrl, String fileName, long fileSize, String contentType) {}
}
