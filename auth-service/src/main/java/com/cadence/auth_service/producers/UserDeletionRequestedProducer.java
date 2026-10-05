package com.cadence.auth_service.producers;

import com.cadence.events.Topics;
import com.cadence.events.UserDeletionRequestedEvent;
import com.cadence.messaging.outbox.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDeletionRequestedProducer {
    private final OutboxPublisher outboxPublisher;

    public void send(String userId) {
        outboxPublisher.publish(Topics.USER_DELETION_REQUESTED_TOPIC, "user", userId, new UserDeletionRequestedEvent(userId));
    }
}
