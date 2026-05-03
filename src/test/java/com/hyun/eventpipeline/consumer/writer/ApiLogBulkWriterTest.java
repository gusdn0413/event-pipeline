package com.hyun.eventpipeline.consumer.writer;

import com.hyun.eventpipeline.consumer.ack.Acknowledgment;
import com.hyun.eventpipeline.consumer.mapper.ApiLogMapper;
import com.hyun.eventpipeline.consumer.model.ApiLog;
import com.hyun.eventpipeline.consumer.model.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ApiLogBulkWriterTest {

    private ApiLogMapper mapper;
    private ApiLogBulkWriter writer;

    @BeforeEach
    void setUp() {
        mapper = mock(ApiLogMapper.class);
        writer = new ApiLogBulkWriter(mapper);
    }

    @Test
    @DisplayName("accept 후 flush → 쌓인 건이 FIFO 순서대로 mapper 에 전달")
    void flush_insertsBufferedLogsInOrder() {
        writer.accept(sampleLog("u1"), Acknowledgment.NOOP);
        writer.accept(sampleLog("u2"), Acknowledgment.NOOP);
        writer.accept(sampleLog("u3"), Acknowledgment.NOOP);

        writer.flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ApiLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertApiLogBulk(captor.capture());

        assertThat(captor.getValue())
                .extracting(ApiLog::getUserId)
                .containsExactly("u1", "u2", "u3");
    }

    @Test
    @DisplayName("한 번의 flush 는 최대 BATCH_SIZE 건만 처리")
    void flush_respectsBatchSize() {
        IntStream.rangeClosed(1, 15).forEach(i -> writer.accept(sampleLog("u" + i), Acknowledgment.NOOP));

        writer.flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ApiLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertApiLogBulk(captor.capture());
        assertThat(captor.getValue()).hasSize(10);
    }

    @Test
    @DisplayName("capacity(500) 초과 accept → 즉시 nak 호출 (JetStream redeliver 유도)")
    void accept_overCapacity_naks() {
        Acknowledgment ack = mock(Acknowledgment.class);

        // 505건 넣으면 500건은 enqueue, 5건은 nak
        IntStream.rangeClosed(1, 505).forEach(i -> writer.accept(sampleLog("u" + i), ack));

        verify(ack, times(5)).nak();
        verify(ack, never()).ack();
    }

    @Test
    @DisplayName("flush 성공 시 batch 내 모든 ack 호출")
    void flush_success_acksAll() {
        Acknowledgment a1 = mock(Acknowledgment.class);
        Acknowledgment a2 = mock(Acknowledgment.class);
        writer.accept(sampleLog("u1"), a1);
        writer.accept(sampleLog("u2"), a2);

        writer.flush();

        verify(a1).ack();
        verify(a2).ack();
        verify(a1, never()).nak();
        verify(a2, never()).nak();
    }

    @Test
    @DisplayName("flush 실패(DB 장애) 시 batch 내 모든 nak 호출")
    void flush_failure_naksAll() {
        Acknowledgment a1 = mock(Acknowledgment.class);
        Acknowledgment a2 = mock(Acknowledgment.class);
        writer.accept(sampleLog("u1"), a1);
        writer.accept(sampleLog("u2"), a2);
        doThrow(new RuntimeException("db down")).when(mapper).insertApiLogBulk(org.mockito.ArgumentMatchers.anyList());

        writer.flush();

        verify(a1).nak();
        verify(a2).nak();
        verify(a1, never()).ack();
        verify(a2, never()).ack();
    }

    private ApiLog sampleLog(String userId) {
        return ApiLog.builder()
                .userId(userId)
                .agent("phone")
                .targetId("1")
                .eventType(EventType.SUCCESS)
                .httpMethod("GET")
                .endpoint("/api/products/search")
                .statusCode(200)
                .responseTime(50)
                .errorCode(null)
                .callAt(LocalDateTime.now())
                .build();
    }
}
