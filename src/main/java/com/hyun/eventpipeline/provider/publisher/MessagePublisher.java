package com.hyun.eventpipeline.provider.publisher;

// 메시지 브로커 추상화 (NATS, EventListener)
public interface MessagePublisher {

    void publish(String message);
}
