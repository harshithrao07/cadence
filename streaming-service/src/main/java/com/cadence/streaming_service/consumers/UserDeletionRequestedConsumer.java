package com.cadence.streaming_service.consumers;

import com.cadence.events.Topics;
import com.cadence.events.UserDataPurgedEvent;
import com.cadence.events.UserDeletionRequestedEvent;
import com.cadence.messaging.inbox.IdempotentEventHandler;
import com.cadence.messaging.outbox.OutboxPublisher;
import com.cadence.streaming_service.repository.PlayHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Account deletion saga participant: removes the user's play history, then confirms to auth-service in the same
 * transaction. Idempotent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletionRequestedConsumer {
    static final String HANDLER = "streaming.purge-user";

    private final IdempotentEventHandler eventHandler;
    private final PlayHistoryRepository playHistoryRepository;
    private final OutboxPublisher outboxPublisher;

    @KafkaListener(topics = Topics.USER_DELETION_REQUESTED_TOPIC, groupId = "streaming-service")
    public void handleUserDeletionRequested(String message) {
        eventHandler.handle(HANDLER, message, UserDeletionRequestedEvent.class, envelope -> {
            String userId = envelope.payload().userId();
            int rows = playHistoryRepository.deleteByUserId(userId);

            outboxPublisher.publish(Topics.STREAMING_USER_DATA_PURGED_TOPIC, "user", userId,
                    new UserDataPurgedEvent(userId, "streaming"));
            log.info("Purged user {}: {} play-history rows", userId, rows);
        });
    }
}
