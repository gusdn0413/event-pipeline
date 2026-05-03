package com.hyun.eventpipeline;

import io.nats.client.Connection;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class EventPipelineApplicationTests {

    /**
     * 통합테스트는 실제 NATS 브로커가 없으므로 Connection을 mock으로 대체.
     * createDispatcher().subscribe() 체인을 NPE 없이 받기 위해 deep stubs 사용.
     */
    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private Connection natsConnection;

    @Test
    void contextLoads() {
    }

}
