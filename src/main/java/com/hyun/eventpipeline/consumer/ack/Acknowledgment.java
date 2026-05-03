package com.hyun.eventpipeline.consumer.ack;

/**
 * 브로커별 ack/nak 동작을 추상화
 */
public interface Acknowledgment {

    void ack();

    void nak();

    Acknowledgment NOOP = new Acknowledgment() {
        @Override
        public void ack() {
        }

        @Override
        public void nak() {
        }
    };
}
