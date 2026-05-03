package com.hyun.eventpipeline.config;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Slf4j
@Getter
@Configuration
@ConditionalOnProperty(prefix = "messaging", name = "broker", havingValue = "nats", matchIfMissing = true)
public class NatsConfig {

    @Value("${nats.url}")
    private String url;

    @Value("${nats.subject}")
    private String subject;

    @Value("${nats.jetstream.stream}")
    private String streamName;

    @Value("${nats.jetstream.consumer}")
    private String consumerName;

    @Value("${nats.jetstream.max-age-seconds}")
    private long maxAgeSeconds;

    @Value("${nats.jetstream.ack-wait-seconds}")
    private long ackWaitSeconds;

    @Value("${nats.jetstream.max-deliver}")
    private int maxDeliver;

    @Bean(destroyMethod = "close")
    public Connection natsConnection() throws Exception {
        Options options = new Options.Builder()
                .server(url)
                // 일시 장애시 자동 복구
                .maxReconnects(-1)
                .reconnectWait(Duration.ofSeconds(2))
                .connectionListener((conn, type) ->
                        log.info("[nats] {} (status={})", type, conn.getStatus()))
                .build();
        return Nats.connect(options);
    }

    /**
     * JetStream 스트림을 idempotent하게 생성.
     * WorkQueuePolicy: consumer가 ack한 메시지는 즉시 삭제 → 디스크 사용 = consumer lag.
     * MaxAge=1h는 consumer가 완전히 멈춰도 디스크가 무한 증가하지 않게 막는 안전망.
     */
    @Bean
    public JetStream jetStream(Connection natsConnection) throws IOException, JetStreamApiException {
        JetStreamManagement jsm = natsConnection.jetStreamManagement();
        StreamConfiguration desired = StreamConfiguration.builder()
                .name(streamName)
                .subjects(List.of(subject))
                .retentionPolicy(RetentionPolicy.WorkQueue)
                .storageType(StorageType.File)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();

        try {
            jsm.addStream(desired);
            log.info("[nats] stream created: {}", streamName);
        } catch (JetStreamApiException e) {
            // 이미 존재하면 update로 설정 동기화
            jsm.updateStream(desired);
            log.info("[nats] stream updated: {}", streamName);
        }
        return natsConnection.jetStream();
    }
}
