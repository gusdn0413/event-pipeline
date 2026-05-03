package com.hyun.eventpipeline.provider.publisher;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;


// Spring ApplicationEvent 기반 in-process publisher.
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "messaging", name = "broker", havingValue = "spring-event")
public class SpringEventMessagePublisher implements MessagePublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publish(String message) {
        eventPublisher.publishEvent(message);
    }
}
