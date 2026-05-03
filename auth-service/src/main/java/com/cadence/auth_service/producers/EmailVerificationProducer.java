package com.cadence.auth_service.producers;

import com.cadence.auth_service.dto.Topics;
import com.cadence.auth_service.events.EmailVerificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationProducer {
    private final KafkaTemplate<String, EmailVerificationEvent> kafkaTemplate;

    public void send(EmailVerificationEvent event) {
        log.info("Attempting to send message to topic: {}, email: {}", Topics.EMAIL_VERIFICATION_TOPIC, event.getEmail());
        kafkaTemplate.send(Topics.EMAIL_VERIFICATION_TOPIC, event.getEmail(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Message sent successfully to topic: {}, email: {}", Topics.EMAIL_VERIFICATION_TOPIC, event.getEmail());
                    } else {
                        log.error("Failed to send message to topic: {}, email: {}", Topics.EMAIL_VERIFICATION_TOPIC, event.getEmail(), ex);
                    }
                });
    }
}
