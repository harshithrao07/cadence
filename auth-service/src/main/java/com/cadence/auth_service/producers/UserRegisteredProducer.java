package com.cadence.auth_service.producers;

import com.cadence.events.Topics;
import com.cadence.events.UserRegisteredEvent;
import com.cadence.messaging.outbox.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Starts the registration saga for a PENDING user (outbox; delivered after the user's transaction commits).
 */
@Service
@RequiredArgsConstructor
public class UserRegisteredProducer {
    private final OutboxPublisher outboxPublisher;

    public void send(UserRegisteredEvent event) {
        outboxPublisher.publish(Topics.USER_REGISTERED_TOPIC, "user", event.userId(), event);
    }
}
