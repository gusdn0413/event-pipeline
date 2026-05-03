package com.hyun.eventpipeline.provider.publisher;

import com.hyun.eventpipeline.config.NatsConfig;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.api.PublishAck;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// JetStream Publisher
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "messaging", name = "broker", havingValue = "nats", matchIfMissing = true)
public class NatsMessagePublisher implements MessagePublisher {

    private final JetStream jetStream;
    private final NatsConfig natsConfig;

    @Override
    public void publish(String message) {
        try {
            PublishAck ack = jetStream.publish(natsConfig.getSubject(), message.getBytes(StandardCharsets.UTF_8));
            if (ack.hasError()) {
                throw new IllegalStateException("jetstream publish nak: " + ack.getError());
            }
        } catch (IOException | JetStreamApiException e) {
            // 브로커 disconnect/타임아웃 시 예외 → CircuitBreaker가 실패로 카운트
            throw new IllegalStateException("jetstream publish failed", e);
        }
    }
}
