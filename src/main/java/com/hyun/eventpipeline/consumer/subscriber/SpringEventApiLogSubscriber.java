package com.hyun.eventpipeline.consumer.subscriber;

import com.hyun.eventpipeline.consumer.extractor.ApiLogExtractor;
import com.hyun.eventpipeline.consumer.model.ApiLog;
import com.hyun.eventpipeline.consumer.ack.Acknowledgment;
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

    @Async
    @EventListener
    public void onMessage(String message) {
        try {
            ApiLog apiLog = extractor.extract(message);
            if (apiLog == null) return;
            // in-process 전달이라 재배달 개념이 없으므로 NOOP
            writer.accept(apiLog, Acknowledgment.NOOP);
        } catch (Exception e) {
            log.warn("message handling failed: {}", e.getMessage());
        }
    }
}
