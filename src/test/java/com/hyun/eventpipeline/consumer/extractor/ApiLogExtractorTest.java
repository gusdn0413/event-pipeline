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

    @Test
    @DisplayName("AUTH_LOGIN (token 없음) → requestData.userId 폴백")
    void extract_authLoginFallsBackToRequestUserId() {
        String json = """
                {
                  "endpoint": "/api/auth/login",
                  "method": "POST",
                  "callAt": "2026-05-01T10:00:00",
                  "responseTime": 50,
                  "header": { "agent": "desktop" },
                  "requestData": { "userId": "임준희" },
                  "responseData": { "statusCode": 200, "errorCode": null }
                }
                """;

        ApiLog log = extractor.extract(json);

        assertThat(log.getUserId()).isEqualTo("임준희");
        assertThat(log.getTargetId()).isNull();
    }

    @Test
    @DisplayName("statusCode 4xx → CLIENT_ERROR")
    void classify_clientError() {
        assertThat(extractor.extract(baseJson(401, 50, "UNAUTHORIZED")).getEventType())
                .isEqualTo(EventType.CLIENT_ERROR);
    }

    @Test
    @DisplayName("statusCode 5xx → SERVER_ERROR (responseTime 무관)")
    void classify_serverError() {
        // responseTime 1500ms 라도 5xx 가 우선 — SLOW 가 아니라 SERVER_ERROR
        assertThat(extractor.extract(baseJson(503, 1500, "SERVICE_UNAVAILABLE")).getEventType())
                .isEqualTo(EventType.SERVER_ERROR);
    }

    private String baseJson(int statusCode, int responseTime, String errorCode) {
        String errorCodeJson = (errorCode == null) ? "null" : "\"" + errorCode + "\"";
        return """
                {
                  "endpoint": "/api/products/search",
                  "method": "GET",
                  "callAt": "2026-05-01T14:00:00",
                  "responseTime": %d,
                  "header": { "token": "tok_임준희", "agent": "phone" },
                  "requestData": { "productId": "1" },
                  "responseData": { "statusCode": %d, "errorCode": %s }
                }
                """.formatted(responseTime, statusCode, errorCodeJson);
    }
}
