package com.chatops.global.config;

import io.minio.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.minio")
public class MinioConfig {
    private String endpoint;
    private int port;
    private String accessKey;
    private String secretKey;
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
            .endpoint(endpoint, port, false)
            .credentials(accessKey, secretKey)
            .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initBucket() {
        try {
            MinioClient client = MinioClient.builder()
                .endpoint(endpoint, port, false)
                .credentials(accessKey, secretKey)
                .build();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket '{}' created", bucket);
            } else {
                log.info("MinIO bucket '{}' already exists", bucket);
            }
        } catch (Exception e) {
            // 멀티 인스턴스 환경에서 동시 생성 시도 시 "already own it" 에러는 정상
            if (e.getMessage() != null && e.getMessage().contains("you already own it")) {
                log.info("MinIO bucket '{}' already exists (concurrent creation)", bucket);
            } else {
                log.error("Failed to initialize MinIO bucket: {}", e.getMessage());
            }
        }
    }
}
