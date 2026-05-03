package com.hyun.eventpipeline.provider.simulator;

import com.hyun.eventpipeline.provider.publisher.MessagePublisher;
import com.hyun.eventpipeline.provider.config.EventConfig;
import com.hyun.eventpipeline.provider.config.EventConfig.Weights;
import com.hyun.eventpipeline.provider.model.ApiCall;
import com.hyun.eventpipeline.provider.model.CallResult;
import com.hyun.eventpipeline.provider.model.Product;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "event.generator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ApiCallSimulator {

    // 사용자 목록
    private static final List<String> USERS = List.of("박현우", "이강현", "지수근", "손규정", "임준희");

    // 디바이스 타입
    private static final List<String> AGENTS = List.of("phone", "desktop");

    // 상품 목록
    private static final List<Product> PRODUCTS = List.of(
            new Product("1", "mouse"),
            new Product("2", "keyboard"),
            new Product("3", "monitor"),
            new Product("4", "speaker"),
            new Product("5", "phone")
    );

    private static final int SUCCESS_STATUS = 200;
    private static final List<Integer> CLIENT_STATUS = List.of(400, 401, 403);
    private static final List<Integer> SERVER_STATUS = List.of(500, 500, 502, 503);

    private final EventConfig eventConfig;
    private final ObjectMapper objectMapper;
    private final MessagePublisher messagePublisher;
    private final CircuitBreaker brokerCircuitBreaker;

    // API 호출 시뮬레이터 : 매 호출마다 50~1000ms 랜덤 대기 후 메시지 발행
    @Scheduled(fixedDelay = 1)
    public void provide() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(50, 1001));

            ApiCall apiCall = pick(ApiCall.values());
            String userId = pick(USERS);

            CallResult callResult = selectCallResult();
            int statusCode = selectStatus(callResult);
            int responseTime = selectResponseTime(callResult);
            String errorCode = selectErrorCode(statusCode);

            String message = buildMessage(apiCall, userId, statusCode, errorCode, responseTime, LocalDateTime.now());

            // 메시지 브로커로 발행 (NATS / Spring Event 중 활성화된 구현체)
            // Circuit Breaker로 감싸 브로커 장애 감지 시 일정 시간 OPEN 시켜 호출 차단
            try {
                brokerCircuitBreaker.executeRunnable(() -> messagePublisher.publish(message));
                log.info("[{}] published: {}", apiCall.getDescription(), message);
            } catch (CallNotPermittedException e) {
                log.warn("[{}] dropped — circuit OPEN", apiCall.getDescription());
            } catch (Exception e) {
                log.warn("[{}] publish failed: {}", apiCall.getDescription(), e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("api call failed", e);
        }
    }

    private String buildMessage(ApiCall apiCall, String userId, int statusCode, String errorCode, int responseTime, LocalDateTime callAt) throws Exception {

        // 헤더 값 세팅
        Map<String, String> header = new LinkedHashMap<>();
        header.put("agent", pick(AGENTS));

        // 실제 API 호출 시 헤더의 token 에서 user_id 추출한다고 가정
        if (apiCall != ApiCall.AUTH_LOGIN) {
            header.put("token", "tok_" + userId);
        }

        // 응답 값 세팅
        Map<String, Object> responseData = new LinkedHashMap<>();

        responseData.put("statusCode", statusCode);
        responseData.put("errorCode", errorCode);

        // 요청 값 세팅
        Map<String, String> requestData = new LinkedHashMap<>();
        boolean success = errorCode == null;

        switch (apiCall) {
            case AUTH_LOGIN -> requestData.put("userId", userId);
            case PRODUCT_SEARCH -> {
                Product product = pick(PRODUCTS);
                requestData.put("productId", product.getId());
                if (success) responseData.put("keyword", product.getName());
            }
            case ORDER_CREATE -> {
                Product product = pick(PRODUCTS);
                requestData.put("productId", product.getId());
                if (success)
                    responseData.put("orderId", String.valueOf(ThreadLocalRandom.current().nextInt(1, 10000)));
            }
            case ORDER_DELETE ->
                    requestData.put("orderId", String.valueOf(ThreadLocalRandom.current().nextInt(1, 10000)));
        }

        // 실제 컨슈머에 전송할 메세지 세팅
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("endpoint", apiCall.getEndpoint());
        message.put("method", apiCall.getMethod().name());
        message.put("callAt", callAt.toString());
        message.put("responseTime", responseTime);
        message.put("header", header);
        message.put("requestData", requestData);
        message.put("responseData", responseData);

        return objectMapper.writeValueAsString(message);
    }

    /**
     * yml의 weights(예: 70/15/5/10) 비율대로 outcome을 가중치 랜덤으로 선택.
     * 균등한 확률의 랜덤을 안 쓰는 이유는 실제 트래픽 분포가 성공에 치우쳐 있어서 — 비율을 yml에서 튜닝 가능하게 분리.
     */
    private CallResult selectCallResult() {
        Weights w = eventConfig.getWeights();
        int total = w.getSuccess() + w.getClientError() + w.getServerError() + w.getSlow();
        int roll = ThreadLocalRandom.current().nextInt(total);
        if (roll < w.getSuccess()) return CallResult.SUCCESS;
        roll -= w.getSuccess();
        if (roll < w.getClientError()) return CallResult.CLIENT_ERROR;
        roll -= w.getClientError();
        if (roll < w.getServerError()) return CallResult.SERVER_ERROR;
        return CallResult.SLOW;
    }

    private int selectStatus(CallResult callResult) {
        return switch (callResult) {
            case SUCCESS, SLOW -> SUCCESS_STATUS;
            case CLIENT_ERROR -> pick(CLIENT_STATUS);
            case SERVER_ERROR -> pick(SERVER_STATUS);
        };
    }

    private int selectResponseTime(CallResult callResult) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return switch (callResult) {
            case SUCCESS -> r.nextInt(10, 300);
            case CLIENT_ERROR -> r.nextInt(5, 100);
            case SERVER_ERROR -> r.nextInt(100, 2000);
            case SLOW -> r.nextInt(1000, 3000);
        };
    }

    private String selectErrorCode(int statusCode) {
        return switch (statusCode) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 500 -> "INTERNAL_ERROR";
            case 502 -> "BAD_GATEWAY";
            case 503 -> "SERVICE_UNAVAILABLE";
            default -> null;
        };
    }

    private static <T> T pick(List<T> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    private static <T> T pick(T[] arr) {
        return arr[ThreadLocalRandom.current().nextInt(arr.length)];
    }
}
