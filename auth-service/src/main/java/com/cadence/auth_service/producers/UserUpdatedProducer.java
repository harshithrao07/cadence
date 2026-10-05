package com.cadence.auth_service.producers;

import com.cadence.auth_service.model.User;
import com.cadence.events.Topics;
import com.cadence.events.UserUpdatedEvent;
import com.cadence.messaging.outbox.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Publishes the user's current public profile whenever it is created or changes, so other services' replicas
 * (catalog's user_replica) stay in sync. Must be called in the transaction that saved the user.
 */
@Service
@RequiredArgsConstructor
public class UserUpdatedProducer {
    private final OutboxPublisher outboxPublisher;

    public void send(User user) {
        outboxPublisher.publish(Topics.USER_UPDATED_TOPIC, "user", user.getId(),
                new UserUpdatedEvent(user.getId(), user.getName(), user.getEmail(), user.getProfileUrl()));
    }
}
