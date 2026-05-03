package com.hyun.eventpipeline.config;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Getter
@Configuration
@ConditionalOnProperty(prefix = "messaging", name = "broker", havingValue = "nats", matchIfMissing = true)
public class NatsConfig {

    @Value("${nats.url}")
    private String url;

    @Value("${nats.subject}")
    private String subject;

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
}
