package com.hyun.eventpipeline.provider.model;

// provider가 발행하는 API 호출 메시지. consumer가 @EventListener로 수신
public record ApiCallMessageEvent(String message) {
}
