package com.hyun.eventpipeline.consumer.writer;

import com.hyun.eventpipeline.consumer.ack.Acknowledgment;
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

    private final LinkedBlockingQueue<BufferedLog> apiLogsBuffer = new LinkedBlockingQueue<>(CAPACITY);
    private final ApiLogMapper apiLogMapper;

    // listener가 메시지 추출 후 호출. 버퍼 풀이면 nak → JetStream이 redeliver
    public void accept(ApiLog apiLog, Acknowledgment ack) {
        if (!apiLogsBuffer.offer(new BufferedLog(apiLog, ack))) {
            log.warn("buffer full (capacity={}), nak 1 api log", CAPACITY);
            ack.nak();
        }
    }

    // 1초마다 버퍼에서 최대 BATCH_SIZE건 꺼내 bulk insert. DB write 성공 시 일괄 ack, 실패 시 nak
    @Scheduled(fixedDelay = 1000)
    public void flush() {
        List<BufferedLog> batch = new ArrayList<>(BATCH_SIZE);
        for (int i = 0; i < BATCH_SIZE; i++) {
            BufferedLog buffered = apiLogsBuffer.poll();
            if (buffered == null) break;
            batch.add(buffered);
        }
        if (batch.isEmpty()) return;

        try {
            // 잦은 단건 INSERT로 인한 DB 부하를 방지하기 위해 메시지를 모아 1초마다 Bulk INSERT
            apiLogMapper.insertApiLogBulk(batch.stream().map(BufferedLog::getLog).toList());
            batch.forEach(b -> b.getAck().ack());
            log.info("bulk inserted {} api logs", batch.size());
        } catch (Exception e) {
            // DB 장애 시 nak → JetStream이 ackWait 후 redeliver
            batch.forEach(b -> b.getAck().nak());
            log.warn("bulk insert failed, nak {} logs: {}", batch.size(), e.getMessage());
        }
    }
}
