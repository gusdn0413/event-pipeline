package com.hyun.eventpipeline.provider.publisher;

/**
 * 메시지 브로커 추상화.
 * 구현체로 NATS / Spring ApplicationEvent 두 가지를 두고 messaging.broker 프로퍼티로 스위칭.
 */
public interface MessagePublisher {

    void publish(String message);
}
