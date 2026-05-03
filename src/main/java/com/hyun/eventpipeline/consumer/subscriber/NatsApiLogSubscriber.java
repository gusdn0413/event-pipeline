package com.hyun.eventpipeline.consumer.subscriber;

import com.hyun.eventpipeline.config.NatsConfig;
import com.hyun.eventpipeline.consumer.ack.NatsAcknowledgment;
import com.hyun.eventpipeline.consumer.extractor.ApiLogExtractor;
import com.hyun.eventpipeline.consumer.model.ApiLog;
import com.hyun.eventpipeline.consumer.writer.ApiLogBulkWriter;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "messaging", name = "broker", havingValue = "nats", matchIfMissing = true)
public class NatsApiLogSubscriber {

    private final ApiLogExtractor extractor;
    private final ApiLogBulkWriter writer;
    private final Connection natsConnection;
    private final JetStream jetStream;
    private final NatsConfig natsConfig;

    @PostConstruct
    public void subscribe() throws IOException, JetStreamApiException {
        ConsumerConfiguration cc = ConsumerConfiguration.builder()
                .durable(natsConfig.getConsumerName())
                .ackPolicy(AckPolicy.Explicit)
                .ackWait(Duration.ofSeconds(natsConfig.getAckWaitSeconds()))
                .maxDeliver(natsConfig.getMaxDeliver())
                .build();

        PushSubscribeOptions opts = PushSubscribeOptions.builder()
                .stream(natsConfig.getStreamName())
                .configuration(cc)
                .build();

        Dispatcher dispatcher = natsConnection.createDispatcher();
        jetStream.subscribe(natsConfig.getSubject(), dispatcher, this::onMessage, false, opts);
        log.info("[nats] jetstream subscribed: stream={}, consumer={}",
                natsConfig.getStreamName(), natsConfig.getConsumerName());
    }

    private void onMessage(Message msg) {
        try {
            String payload = new String(msg.getData(), StandardCharsets.UTF_8);
            ApiLog apiLog = extractor.extract(payload);
            if (apiLog == null) {
                // 파싱 실패는 redeliver해도 성공 못함 -> ack로 종료
                msg.ack();
                return;
            }
            writer.accept(apiLog, new NatsAcknowledgment(msg));
        } catch (Exception e) {
            log.warn("message handling failed, nak: {}", e.getMessage());
            msg.nak();
        }
    }
}
