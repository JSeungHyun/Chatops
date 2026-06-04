package com.chatops.global.metrics;

import com.chatops.global.config.RabbitMQConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 6주차: Micrometer 커스텀 Prometheus 메트릭 등록/수집.
 *
 * <p>메트릭 종류
 * <ul>
 *   <li>{@code ws_connections_active} (Gauge) — 현재 WebSocket 동시 접속 세션 수</li>
 *   <li>{@code messages_sent_total} (Counter) — 전체 메시지 전송 수</li>
 *   <li>{@code messages_sent_per_room} (Counter, tag=room) — 채팅방별 메시지 수 (카디널리티는 {@link MetricsConfig}에서 상한)</li>
 *   <li>{@code file_upload_total} (Counter) — 파일 업로드 수</li>
 *   <li>{@code rabbitmq_queue_length} (Gauge, tag=queue) — 큐별 대기 메시지 수</li>
 *   <li>{@code redis_memory_usage_bytes} (Gauge) — Redis used_memory</li>
 * </ul>
 *
 * <p>외부 시스템(RabbitMQ/Redis) 값은 Gauge supplier에서 직접 조회하지 않고
 * {@link #pollExternalMetrics()} 스케줄러가 주기적으로 캐시(atomic)에 채운다.
 * Gauge supplier가 I/O를 하면 매 스크랩(5s)마다 외부 호출이 발생하므로, 폴링 주기를 스크랩 주기와 분리한다.
 *
 * <p>{@code api_request_duration_seconds}(p50/p95/p99)는 Spring Boot Actuator가
 * {@code http_server_requests_seconds_*}로 자동 수집하므로 여기서 중복 등록하지 않는다.
 */
@Slf4j
@Service
public class MetricsService {

    private final MeterRegistry registry;
    private final AmqpAdmin amqpAdmin;
    private final StringRedisTemplate redisTemplate;

    /** 동시 접속 세션 카운트. Gauge가 스크랩 시점에 값을 읽어간다. */
    private final AtomicInteger wsConnections = new AtomicInteger(0);

    private Counter messagesSentTotal;
    private Counter fileUploadTotal;

    /** 폴링 결과 캐시 — Gauge supplier는 이 atomic만 읽고(무 I/O), 스케줄러가 갱신한다. */
    private final Map<String, AtomicLong> queueLengths = new ConcurrentHashMap<>();
    private final AtomicLong redisMemoryBytes = new AtomicLong(0);

    /** 폴링 대상 RabbitMQ 큐 목록 (RabbitMQConfig 상수 재사용으로 큐 이름 단일 출처화). */
    private static final List<String> MONITORED_QUEUES = List.of(
        RabbitMQConfig.NOTIFICATION_QUEUE,
        RabbitMQConfig.READ_RECEIPT_QUEUE,
        RabbitMQConfig.FILE_PROCESS_QUEUE
    );

    private static final long POLL_INTERVAL_MS = 15_000L;

    public MetricsService(MeterRegistry registry, AmqpAdmin amqpAdmin, StringRedisTemplate redisTemplate) {
        this.registry = registry;
        this.amqpAdmin = amqpAdmin;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    void registerMetrics() {
        Gauge.builder("ws_connections_active", wsConnections, AtomicInteger::doubleValue)
            .description("현재 WebSocket 동시 접속 세션 수")
            .register(registry);

        messagesSentTotal = Counter.builder("messages_sent_total")
            .description("전체 메시지 전송 수")
            .register(registry);

        fileUploadTotal = Counter.builder("file_upload_total")
            .description("파일 업로드 수")
            .register(registry);

        // 큐별 게이지: 캐시 atomic을 state로 등록 (supplier는 I/O 없음)
        for (String queue : MONITORED_QUEUES) {
            AtomicLong holder = new AtomicLong(0);
            queueLengths.put(queue, holder);
            Gauge.builder("rabbitmq_queue_length", holder, AtomicLong::doubleValue)
                .tag("queue", queue)
                .description("RabbitMQ 큐 대기 메시지 수")
                .register(registry);
        }

        Gauge.builder("redis_memory_usage_bytes", redisMemoryBytes, AtomicLong::doubleValue)
            .description("Redis 메모리 사용량(used_memory, bytes)")
            .register(registry);

        // 시작 직후 1회 채워 스케줄러 첫 실행(약 15s 후) 전까지 0이 노출되지 않게 한다
        pollExternalMetrics();
        log.info("Custom Prometheus metrics registered");
    }

    // ===== WebSocket 동시 접속 =====

    public void incrementWsConnections() {
        wsConnections.incrementAndGet();
    }

    public void decrementWsConnections() {
        // 음수 방어: disconnect 이벤트가 중복 전달되어도 0 미만으로 내려가지 않게 한다
        wsConnections.updateAndGet(current -> current > 0 ? current - 1 : 0);
    }

    // ===== 메시지 전송 =====

    public void recordMessageSent(String roomId) {
        messagesSentTotal.increment();
        // 채팅방별 카운터 (tag=room). 고유 room 태그 수는 MetricsConfig의 MeterFilter로 상한 처리.
        registry.counter("messages_sent_per_room", "room", roomId).increment();
    }

    // ===== 파일 업로드 =====

    public void recordFileUpload() {
        fileUploadTotal.increment();
    }

    // ===== 외부 시스템 폴링 (스크랩 주기와 분리) =====

    /**
     * RabbitMQ 큐 길이와 Redis 메모리를 주기적으로 조회해 캐시 atomic에 반영한다.
     * 실패 시 직전 값을 유지하지 않고 0으로 둬, 일시 장애가 오래된 값으로 오인되지 않게 한다.
     */
    @Scheduled(fixedRate = POLL_INTERVAL_MS)
    void pollExternalMetrics() {
        for (Map.Entry<String, AtomicLong> entry : queueLengths.entrySet()) {
            entry.getValue().set(queueLength(entry.getKey()));
        }
        redisMemoryBytes.set(redisMemoryUsage());
    }

    private long queueLength(String queueName) {
        try {
            QueueInformation info = amqpAdmin.getQueueInfo(queueName);
            return info != null ? info.getMessageCount() : 0L;
        } catch (Exception e) {
            log.debug("rabbitmq_queue_length poll failed: queue={}, err={}", queueName, e.getMessage());
            return 0L;
        }
    }

    private long redisMemoryUsage() {
        try {
            Properties info = redisTemplate.execute(
                (RedisCallback<Properties>) connection -> connection.serverCommands().info("memory"));
            if (info == null) {
                return 0L;
            }
            String usedMemory = info.getProperty("used_memory");
            return usedMemory != null ? Long.parseLong(usedMemory) : 0L;
        } catch (Exception e) {
            log.debug("redis_memory_usage_bytes poll failed: err={}", e.getMessage());
            return 0L;
        }
    }
}
