package com.hyun.eventpipeline.provider.config;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerConfigurationTest {

    private CircuitBreaker cb;

    @BeforeEach
    void setUp() {
        cb = new CircuitBreakerConfig().brokerCircuitBreaker();
    }

    @Test
    @DisplayName("연속 5회 실패 시 OPEN 으로 전환되어 이후 호출이 차단됨")
    void open_after_five_failures_blocks_subsequent_calls() {
        failNTimes(5);

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> cb.executeRunnable(() -> {}))
                .isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    @DisplayName("HALF_OPEN 에서 성공하면 CLOSED 로 복귀")
    void half_open_recovers_to_closed_on_success() {
        failNTimes(5);
        cb.transitionToHalfOpenState();

        cb.executeRunnable(() -> {});

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private void failNTimes(int n) {
        for (int i = 0; i < n; i++) {
            try {
                cb.executeRunnable(() -> { throw new RuntimeException("fail"); });
            } catch (RuntimeException ignored) {
            }
        }
    }
}
