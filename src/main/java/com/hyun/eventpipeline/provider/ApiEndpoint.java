package com.hyun.eventpipeline.provider;

import lombok.Getter;
import org.springframework.http.HttpMethod;

@Getter
public enum ApiEndpoint {

    AUTH_LOGIN("로그인", HttpMethod.POST, "/api/auth/login"),
    PRODUCT_SEARCH("상품 검색", HttpMethod.GET, "/api/products/search"),
    ORDER_CREATE("주문 생성", HttpMethod.POST, "/api/orders"),
    ORDER_DELETE("주문 삭제", HttpMethod.DELETE, "/api/orders/{orderId}");

    private final String description;
    private final HttpMethod method;
    private final String pathTemplate;

    ApiEndpoint(String description, HttpMethod method, String pathTemplate) {
        this.description = description;
        this.method = method;
        this.pathTemplate = pathTemplate;
    }
}
