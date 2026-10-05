package com.project.cadence.producers;

import com.cadence.events.RecordCreatedEvent;
import com.cadence.events.Topics;
import com.cadence.messaging.outbox.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Records {@link RecordCreatedEvent} in the outbox; the relay delivers it after the record's transaction commits.
 */
@Service
@RequiredArgsConstructor
public class RecordCreatedProducer {
    private final OutboxPublisher outboxPublisher;

    public void send(RecordCreatedEvent event) {
        outboxPublisher.publish(Topics.RECORD_CREATED_TOPIC, "record", event.recordId(), event);
    }
}
