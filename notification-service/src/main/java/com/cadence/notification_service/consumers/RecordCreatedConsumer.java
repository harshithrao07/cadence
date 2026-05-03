package com.cadence.notification_service.consumers;

import com.cadence.notification_service.events.RecordCreatedEvent;
import com.cadence.notification_service.dto.Topics;
import com.cadence.notification_service.services.WorkerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecordCreatedConsumer {
    private final WorkerService workerService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = Topics.RECORD_CREATED_TOPIC, groupId = "cadence-group")
    public void listenToNewlyCreatedRecord(String payload) throws Exception {
        RecordCreatedEvent event = objectMapper.readValue(payload, RecordCreatedEvent.class);
        workerService.notifyFollowersOfNewRelease(event);
    }
}
