package com.hyun.eventpipeline.provider.model;

import lombok.Getter;
import org.springframework.http.HttpMethod;

@Getter
public enum ApiCall {

    AUTH_LOGIN("로그인", HttpMethod.POST, "/api/auth/login"),
    PRODUCT_SEARCH("상품 검색", HttpMethod.GET, "/api/products/search"),
    ORDER_CREATE("주문 생성", HttpMethod.POST, "/api/orders/create"),
    ORDER_DELETE("주문 삭제", HttpMethod.DELETE, "/api/orders/delete");

    private final String description;
    private final HttpMethod method;
    private final String endpoint;

    ApiCall(String description, HttpMethod method, String endpoint) {
        this.description = description;
        this.method = method;
        this.endpoint = endpoint;
    }
}
