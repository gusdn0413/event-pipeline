package com.hyun.eventpipeline.consumer.extractor;

import com.hyun.eventpipeline.consumer.model.ApiLog;
import com.hyun.eventpipeline.consumer.model.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ApiLogExtractor {

    private static final String TOKEN_PREFIX = "tok_";
    private static final int SLOW_THRESHOLD = 1000;

    private final ObjectMapper objectMapper;

    public ApiLog extract(String message) {
        JsonNode convertedMessage = convertToJsonNode(message);

        JsonNode header = convertedMessage.get("header");
        JsonNode requestData = convertedMessage.get("requestData");
        JsonNode responseData = convertedMessage.get("responseData");

        int statusCode = responseData.get("statusCode").asInt();
        int responseTime = convertedMessage.get("responseTime").asInt();

        return ApiLog.builder()
                .userId(extractUserId(header, requestData))
                .agent(getField(header, "agent"))
                .targetId(getField(requestData, "productId"))
                .eventType(classifyEventType(statusCode, responseTime))
                .httpMethod(convertedMessage.get("method").asString())
                .endpoint(convertedMessage.get("endpoint").asString())
                .statusCode(statusCode)
                .responseTime(responseTime)
                .errorCode(getField(responseData, "errorCode"))
                .callAt(LocalDateTime.parse(convertedMessage.get("callAt").asString()))
                .build();
    }

    // JsonNode 변환
    private JsonNode convertToJsonNode(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid message JSON: " + message, e);
        }
    }

    /**
     * 토큰에 담겨있는 경우 requestData 에 담겨있는 경우 userId 추출
     */
    private String extractUserId(JsonNode header, JsonNode requestData) {
        String token = getField(header, "token");
        if (token != null && token.startsWith(TOKEN_PREFIX)) {
            return token.substring(TOKEN_PREFIX.length());
        }
        return getField(requestData, "userId");
    }

    private String getField(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asString();
    }

    // 상태 코드 별 이벤트 타입 분류
    private EventType classifyEventType(int statusCode, int responseTimeMs) {
        if (statusCode >= 500) return EventType.SERVER_ERROR;
        if (statusCode >= 400) return EventType.CLIENT_ERROR;
        if (responseTimeMs >= SLOW_THRESHOLD) return EventType.SLOW;
        return EventType.SUCCESS;
    }
}
