package com.project.cadence.producers;

import com.project.cadence.dto.Topics;
import com.project.cadence.events.RecordCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordCreatedProducer {
    private final KafkaTemplate<String, RecordCreatedEvent> kafkaTemplate;

    public void send(RecordCreatedEvent event) {
        log.info("Attempting to send message to topic: {}, recordId: {}", Topics.RECORD_CREATED_TOPIC, event.getRecordId());
        kafkaTemplate.send(Topics.RECORD_CREATED_TOPIC, event.getRecordId(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Message sent successfully to topic: {}, recordId: {}", Topics.RECORD_CREATED_TOPIC, event.getRecordId());
                    } else {
                        log.error("Failed to send message to topic: {}, recordId: {}", Topics.RECORD_CREATED_TOPIC, event.getRecordId(), ex);
                    }
                });
    }
}
