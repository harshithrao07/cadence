package com.project.cadence.consumers;

import com.cadence.events.Topics;
import com.cadence.events.UserDataPurgedEvent;
import com.cadence.events.UserDeletionRequestedEvent;
import com.cadence.messaging.inbox.IdempotentEventHandler;
import com.cadence.messaging.outbox.OutboxPublisher;
import com.project.cadence.repository.UserReplicaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Account deletion saga participant: removes the user's artist follows and their replica row, then confirms to
 * auth-service in the same transaction. Idempotent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletionRequestedConsumer {
    static final String HANDLER = "catalog.purge-user";

    private final IdempotentEventHandler eventHandler;
    private final JdbcTemplate jdbcTemplate;
    private final UserReplicaRepository userReplicaRepository;
    private final OutboxPublisher outboxPublisher;

    @KafkaListener(topics = Topics.USER_DELETION_REQUESTED_TOPIC, groupId = "catalog-service")
    public void handleUserDeletionRequested(String message) {
        eventHandler.handle(HANDLER, message, UserDeletionRequestedEvent.class, envelope -> {
            String userId = envelope.payload().userId();
            int follows = jdbcTemplate.update("DELETE FROM artist_following WHERE user_id = ?", userId);
            userReplicaRepository.findById(userId).ifPresent(userReplicaRepository::delete);

            outboxPublisher.publish(Topics.CATALOG_USER_DATA_PURGED_TOPIC, "user", userId,
                    new UserDataPurgedEvent(userId, "catalog"));
            log.info("Purged user {}: {} follows and the replica row", userId, follows);
        });
    }
}
