package com.hyun.eventpipeline.consumer.writer;

import com.hyun.eventpipeline.consumer.ack.Acknowledgment;
import com.hyun.eventpipeline.consumer.model.ApiLog;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * BulkWriter 버퍼에 보관되는 단위. ApiLog와 ack 핸들을 함께 들고 있어
 * DB write 성공/실패에 따라 batch 단위 ack/nak이 가능
 */
@Getter
@AllArgsConstructor
public class BufferedLog {
    private final ApiLog log;
    private final Acknowledgment ack;
}
