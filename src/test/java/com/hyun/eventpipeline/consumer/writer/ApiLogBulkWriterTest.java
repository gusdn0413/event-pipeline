package com.hyun.eventpipeline.consumer.writer;

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
import static org.mockito.Mockito.mock;
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
        writer.accept(sampleLog("u1"));
        writer.accept(sampleLog("u2"));
        writer.accept(sampleLog("u3"));

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
        IntStream.rangeClosed(1, 15).forEach(i -> writer.accept(sampleLog("u" + i)));

        writer.flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ApiLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertApiLogBulk(captor.capture());
        assertThat(captor.getValue()).hasSize(10);
    }

    @Test
    @DisplayName("capacity(500) 초과 accept → 예외 없이 drop")
    void accept_overCapacity_dropsSilently() {
        // 505건 넣고 모두 flush 했을 때 인서트된 총 건수가 500 인지 (5건 drop)
        IntStream.rangeClosed(1, 505).forEach(i -> writer.accept(sampleLog("u" + i)));

        for (int i = 0; i < 60; i++) writer.flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ApiLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper, org.mockito.Mockito.atLeastOnce()).insertApiLogBulk(captor.capture());

        int totalInserted = captor.getAllValues().stream().mapToInt(List::size).sum();
        assertThat(totalInserted).isEqualTo(500);
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
