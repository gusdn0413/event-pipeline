package com.hyun.eventpipeline.consumer.listener;

import com.hyun.eventpipeline.consumer.extractor.ApiLogExtractor;
import com.hyun.eventpipeline.consumer.model.ApiLog;
import com.hyun.eventpipeline.consumer.writer.ApiLogBulkWriter;
import com.hyun.eventpipeline.provider.model.ApiCallMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiLogMessageListener {

    private final ApiLogExtractor extractor;
    private final ApiLogBulkWriter writer;

    // 메시지 브로커 구독 자리 (production에선 NATS/Kafka subscriber로 교체)
    @EventListener
    public void onMessage(ApiCallMessageEvent event) {
        ApiLog apiLog = extractor.extract(event.message());
        if (apiLog == null) return;
        writer.accept(apiLog);
    }
}
