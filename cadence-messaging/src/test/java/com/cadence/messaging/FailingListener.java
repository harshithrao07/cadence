package com.cadence.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class FailingListener {
    static final String TOPIC = "test.always-fails";

    @KafkaListener(topics = TOPIC, groupId = "test-failing-listener")
    void onMessage(String message) {
        throw new IllegalStateException("always fails: " + message);
    }
}
