package com.hyun.eventpipeline.consumer.subscriber;

import com.hyun.eventpipeline.consumer.extractor.ApiLogExtractor;
import com.hyun.eventpipeline.consumer.model.ApiLog;
import com.hyun.eventpipeline.consumer.writer.ApiLogBulkWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "messaging", name = "broker", havingValue = "spring-event")
public class SpringEventApiLogSubscriber {

    private final ApiLogExtractor extractor;
    private final ApiLogBulkWriter writer;

    /**
     * String을 publishEvent하면 Spring이 PayloadApplicationEvent<String>으로 wrap하고,
     * 여기선 페이로드 타입(String)을 그대로 받으면 자동 언래핑.
     * @Async로 발행/구독 스레드 분리 — publisher가 listener의 처리 시간에 영향받지 않음.
     */
    @Async
    @EventListener
    public void onMessage(String message) {
        try {
            ApiLog apiLog = extractor.extract(message);
            if (apiLog == null) return;
            writer.accept(apiLog);
        } catch (Exception e) {
            log.warn("message handling failed: {}", e.getMessage());
        }
    }
}
