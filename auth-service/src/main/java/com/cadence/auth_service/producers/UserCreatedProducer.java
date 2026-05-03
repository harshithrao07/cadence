package com.cadence.auth_service.producers;

import com.cadence.auth_service.dto.Topics;
import com.cadence.auth_service.events.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserCreatedProducer {
    private final KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;

    public void send(UserCreatedEvent event) {
        log.info("Attempting to send message to topic: {}, event: {}", Topics.USER_CREATED_TOPIC, event);
        kafkaTemplate.send(Topics.USER_CREATED_TOPIC, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Message sent successfully to topic: {}, event: {}", Topics.USER_CREATED_TOPIC, event);
                    } else {
                        log.error("Failed to send message to topic: {}, event: {}", Topics.USER_CREATED_TOPIC, event, ex);
                    }
                });
    }
}
