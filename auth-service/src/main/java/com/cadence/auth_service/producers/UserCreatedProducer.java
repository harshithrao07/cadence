package com.cadence.auth_service.producers;

import com.cadence.events.Topics;
import com.cadence.events.UserCreatedEvent;
import com.cadence.messaging.outbox.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Records {@link UserCreatedEvent} in the outbox; the relay delivers it after the user's transaction commits.
 */
@Service
@RequiredArgsConstructor
public class UserCreatedProducer {
    private final OutboxPublisher outboxPublisher;

    public void send(UserCreatedEvent event) {
        outboxPublisher.publish(Topics.USER_CREATED_TOPIC, "user", event.userId(), event);
    }
}
