package com.hyun.eventpipeline.provider.publisher;

import com.hyun.eventpipeline.config.NatsConfig;
import io.nats.client.Connection;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

// Nats Publisher
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "messaging", name = "broker", havingValue = "nats", matchIfMissing = true)
public class NatsMessagePublisher implements MessagePublisher {

    private final Connection natsConnection;
    private final NatsConfig natsConfig;

    @Override
    public void publish(String message) {
        if (natsConnection.getStatus() != Connection.Status.CONNECTED) {
            throw new IllegalStateException("nats not connected: " + natsConnection.getStatus());
        }
        natsConnection.publish(natsConfig.getSubject(), message.getBytes(StandardCharsets.UTF_8));
    }
}
