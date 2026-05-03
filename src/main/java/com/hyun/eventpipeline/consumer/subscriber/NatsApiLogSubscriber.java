package com.hyun.eventpipeline.consumer.subscriber;

import com.hyun.eventpipeline.consumer.extractor.ApiLogExtractor;
import com.hyun.eventpipeline.consumer.model.ApiLog;
import com.hyun.eventpipeline.consumer.writer.ApiLogBulkWriter;
import com.hyun.eventpipeline.config.NatsConfig;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * NATS Dispatcher는 별도 스레드에서 메시지를 처리하므로
 * provider의 publish 스레드와 분리됨 (Spring Event 모드의 @Async 효과와 동일).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "messaging", name = "broker", havingValue = "nats", matchIfMissing = true)
public class NatsApiLogSubscriber {

    private final ApiLogExtractor extractor;
    private final ApiLogBulkWriter writer;
    private final Connection natsConnection;
    private final NatsConfig natsConfig;

    @PostConstruct
    public void subscribe() {
        Dispatcher dispatcher = natsConnection.createDispatcher(msg -> {
            try {
                String payload = new String(msg.getData(), StandardCharsets.UTF_8);
                ApiLog apiLog = extractor.extract(payload);
                if (apiLog == null) return;
                writer.accept(apiLog);
            } catch (Exception e) {
                log.warn("message handling failed: {}", e.getMessage());
            }
        });
        dispatcher.subscribe(natsConfig.getSubject());
        log.info("[nats] subscribed: {}", natsConfig.getSubject());
    }
}
