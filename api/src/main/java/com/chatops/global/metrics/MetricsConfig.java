package com.chatops.global.metrics;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 메트릭 레지스트리 정책 설정.
 */
@Configuration
public class MetricsConfig {

    /** messages_sent_per_room 의 고유 room 태그 허용 상한. */
    private static final int MAX_ROOM_TAGS = 200;

    /**
     * {@code messages_sent_per_room}의 room 태그 카디널리티 상한.
     *
     * <p>room ID는 UUID라 방 수가 늘수록 시계열이 무한 증가(카디널리티 폭발)해
     * Prometheus TSDB/앱 메모리를 압박할 수 있다. 상한을 초과하면 새 room 시계열은 거부되지만
     * {@code messages_sent_total}(무라벨)은 계속 집계되므로 전체 추세는 유지된다.
     */
    @Bean
    public MeterFilter roomCardinalityLimiter() {
        return MeterFilter.maximumAllowableTags(
            "messages_sent_per_room", "room", MAX_ROOM_TAGS, MeterFilter.deny());
    }
}
