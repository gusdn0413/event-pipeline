package com.hyun.eventpipeline;

import com.hyun.eventpipeline.provider.config.EventProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(EventProperties.class)
@EnableScheduling
public class EventPipelineApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventPipelineApplication.class, args);
    }

}
