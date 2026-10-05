package com.cadence.notification_service.consumers;

import com.cadence.events.EventEnvelope;
import com.cadence.events.RecordCreatedEvent;
import com.cadence.events.Topics;
import com.cadence.messaging.EventCodec;
import com.cadence.messaging.SagaContext;
import com.cadence.notification_service.services.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * No database here, so no inbox: delivery is at-least-once and a redelivered event can send duplicate emails.
 */
@Component
@RequiredArgsConstructor
public class RecordCreatedConsumer {
    private final WorkerService workerService;
    private final EventCodec codec;

    @KafkaListener(topics = Topics.RECORD_CREATED_TOPIC, groupId = "cadence-group")
    public void listenToNewlyCreatedRecord(String payload) {
        EventEnvelope<RecordCreatedEvent> envelope = codec.decode(payload, RecordCreatedEvent.class);
        try (SagaContext.Scope ignored = SagaContext.join(envelope.sagaId(), envelope.eventId())) {
            workerService.notifyFollowersOfNewRelease(envelope.payload());
        }
    }
}
