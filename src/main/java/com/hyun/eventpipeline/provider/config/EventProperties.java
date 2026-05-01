package com.hyun.eventpipeline.provider.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "event.generator")
public class EventProperties {

    private boolean enabled;
    private long intervalMs;
    private Weights weights;

    @Getter
    @Setter
    public static class Weights {
        private int success;
        private int clientError;
        private int serverError;
        private int slow;
    }
}
