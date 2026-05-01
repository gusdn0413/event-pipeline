package com.hyun.eventpipeline.consumer.extractor;

import com.hyun.eventpipeline.consumer.model.ApiLog;
import com.hyun.eventpipeline.consumer.model.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ApiLogExtractorTest {

    private ApiLogExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ApiLogExtractor(new JsonMapper());
    }

    @Test
    @DisplayName("PRODUCT_SEARCH 성공 메시지 → 모든 필드가 올바르게 매핑되고 EventType=SUCCESS")
    void extract_productSearchSuccess() {
        String json = """
                {
                  "endpoint": "/api/products/search",
                  "method": "GET",
                  "callAt": "2026-05-01T14:23:45.123",
                  "responseTime": 87,
                  "header": { "token": "tok_park", "agent": "phone" },
                  "requestData": { "productId": "3" },
                  "responseData": { "statusCode": 200, "errorCode": null, "keyword": "monitor" }
                }
                """;

        ApiLog log = extractor.extract(json);

        assertThat(log.getUserId()).isEqualTo("park");
        assertThat(log.getAgent()).isEqualTo("phone");
        assertThat(log.getTargetId()).isEqualTo("3");
        assertThat(log.getEventType()).isEqualTo(EventType.SUCCESS);
        assertThat(log.getHttpMethod()).isEqualTo("GET");
        assertThat(log.getEndpoint()).isEqualTo("/api/products/search");
        assertThat(log.getStatusCode()).isEqualTo(200);
        assertThat(log.getResponseTime()).isEqualTo(87);
        assertThat(log.getErrorCode()).isNull();
        assertThat(log.getCallAt()).isEqualTo(LocalDateTime.parse("2026-05-01T14:23:45.123"));
    }
}
