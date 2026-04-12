package com.chatops.global.queue.consumer;

import com.chatops.domain.file.service.FileService;
import com.chatops.global.config.RabbitMQConfig;
import com.chatops.global.queue.dto.FileProcessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileProcessConsumer {

    private final FileService fileService;

    @RabbitListener(queues = RabbitMQConfig.FILE_PROCESS_QUEUE)
    public void handleFileProcess(FileProcessEvent event) {
        log.info("File process event received: messageId={}, fileUrl={}, processType={}",
            event.getMessageId(), event.getFileUrl(), event.getProcessType());

        if (event.getFileUrl() == null || event.getFileUrl().isBlank()) {
            log.warn("File process event has no fileUrl: messageId={}", event.getMessageId());
            return;
        }

        try {
            String objectKey = event.getFileUrl().replace("/files/download/", "");

            if ("THUMBNAIL".equals(event.getProcessType())) {
                // 썸네일 생성 (MinIO에 저장)
                // Note: thumbnailUrl을 DB에 저장하려면 message 테이블에 thumbnail_url 컬럼 추가 필요
                // 현재는 ddl-auto=validate이므로 썸네일은 MinIO에만 저장하고 로깅
                String thumbnailUrl = fileService.generateThumbnail(objectKey);
                if (thumbnailUrl != null) {
                    log.info("Thumbnail generated for message {}: {}", event.getMessageId(), thumbnailUrl);
                }
            } else if ("METADATA".equals(event.getProcessType())) {
                log.info("File metadata processing for message {}: {}", event.getMessageId(), event.getFileUrl());
            }
        } catch (Exception e) {
            log.error("File processing failed: messageId={}, error={}", event.getMessageId(), e.getMessage());
        }
    }
}
