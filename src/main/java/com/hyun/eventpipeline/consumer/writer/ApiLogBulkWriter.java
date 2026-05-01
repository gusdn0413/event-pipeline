package com.hyun.eventpipeline.consumer.writer;

import com.hyun.eventpipeline.consumer.mapper.ApiLogMapper;
import com.hyun.eventpipeline.consumer.model.ApiLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiLogBulkWriter {

    // 최대 10건 인서트
    private static final int BATCH_SIZE = 10;

    // 버퍼 용량 : API 평균 호출 주기(약 525ms, ~1.9 events/s) 대비 OOM 안 나는 선에서 일시적 DB 지연 흡수 가능한 수준
    private static final int CAPACITY = 500;

    // api log 버퍼
    private final LinkedBlockingQueue<ApiLog> apiLogsBuffer = new LinkedBlockingQueue<>(CAPACITY);
    private final ApiLogMapper apiLogMapper;

    // listener가 메시지 추출 후 호출. 버퍼 가득 차면 경고 로그 + 드롭
    public void accept(ApiLog apiLog) {
        if (!apiLogsBuffer.offer(apiLog)) {
            log.warn("buffer full (capacity={}), dropped 1 api log", CAPACITY);
        }
    }

    // 1초마다 버퍼에서 최대 BATCH_SIZE건 꺼내 bulk insert (비어있으면 skip)
    @Scheduled(fixedDelay = 1000)
    public void flush() {
        List<ApiLog> apiLogs = new ArrayList<>(BATCH_SIZE);
        for (int i = 0; i < BATCH_SIZE; i++) {
            ApiLog apiLog = apiLogsBuffer.poll();
            if (apiLog == null) break;
            apiLogs.add(apiLog);
        }
        if (apiLogs.isEmpty()) return;

        // 잦은 단건 INSERT로 인한 DB 부하를 방지하기 위해 메시지를 모아 1초마다 Bulk INSERT.
        apiLogMapper.insertApiLogBulk(apiLogs);
        log.info("bulk inserted {} api logs", apiLogs.size());
    }
}
