package com.project.cadence.consumers;

import com.cadence.events.Topics;
import com.cadence.events.UserUpdatedEvent;
import com.cadence.messaging.inbox.IdempotentEventHandler;
import com.project.cadence.model.UserReplica;
import com.project.cadence.repository.UserReplicaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code user_replica} in sync with auth-service. Snapshots are upserted; one older than what is stored
 * (e.g. a delayed redelivery from another partition assignment) is ignored.
 */
@Component
@RequiredArgsConstructor
public class UserUpdatedConsumer {
    static final String HANDLER = "catalog.user-replica";

    private final IdempotentEventHandler eventHandler;
    private final UserReplicaRepository userReplicaRepository;

    @KafkaListener(topics = Topics.USER_UPDATED_TOPIC, groupId = "catalog-service")
    public void handleUserUpdated(String message) {
        eventHandler.handle(HANDLER, message, UserUpdatedEvent.class, envelope -> {
            UserUpdatedEvent user = envelope.payload();
            UserReplica replica = userReplicaRepository.findById(user.userId()).orElse(null);
            if (replica != null && replica.getSourceUpdatedAt().isAfter(envelope.occurredAt())) {
                return;
            }
            userReplicaRepository.save(new UserReplica(
                    user.userId(), user.name(), user.email(), user.profileUrl(), envelope.occurredAt()));
        });
    }
}
