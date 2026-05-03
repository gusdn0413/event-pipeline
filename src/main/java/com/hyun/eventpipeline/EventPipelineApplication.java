package com.hyun.eventpipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableAsync는 spring-event 모드의 @EventListener @Async 동작을 위해 활성화 (NATS 모드에선 미사용)
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class EventPipelineApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventPipelineApplication.class, args);
    }

}
