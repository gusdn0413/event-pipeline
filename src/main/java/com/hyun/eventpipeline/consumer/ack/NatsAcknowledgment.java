package com.hyun.eventpipeline.consumer.ack;

import io.nats.client.Message;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class NatsAcknowledgment implements Acknowledgment {

    private final Message msg;

    @Override
    public void ack() {
        msg.ack();
    }

    @Override
    public void nak() {
        msg.nak();
    }
}
