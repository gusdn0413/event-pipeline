package com.hyun.eventpipeline.provider.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class CircuitBreakerConfig {

    // 5회 연속 실패 시 OPEN, 10초 뒤 HALF_OPEN 으로 자동 전환
    @Bean
    public CircuitBreaker brokerCircuitBreaker() {
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config = io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .slidingWindowType(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(5)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(100.0f)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();

        CircuitBreaker cb = CircuitBreaker.of("broker", config);

        cb.getEventPublisher().onStateTransition(e ->
                log.warn("[circuit-breaker] {} -> {}",
                        e.getStateTransition().getFromState(),
                        e.getStateTransition().getToState()));

        return cb;
    }
}
