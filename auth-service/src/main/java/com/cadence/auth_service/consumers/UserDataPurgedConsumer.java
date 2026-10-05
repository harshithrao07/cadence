package com.cadence.auth_service.consumers;

import com.cadence.auth_service.saga.UserDeletionSaga;
import com.cadence.events.Topics;
import com.cadence.events.UserDataPurgedEvent;
import com.cadence.messaging.inbox.IdempotentEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Purge confirmations in the account deletion saga, one topic per participant.
 */
@Component
@RequiredArgsConstructor
public class UserDataPurgedConsumer {
    private final IdempotentEventHandler eventHandler;
    private final UserDeletionSaga userDeletionSaga;

    @KafkaListener(topics = {
            Topics.PLAYLIST_USER_DATA_PURGED_TOPIC,
            Topics.CATALOG_USER_DATA_PURGED_TOPIC,
            Topics.STREAMING_USER_DATA_PURGED_TOPIC
    }, groupId = "auth-service")
    public void handlePurged(String message) {
        eventHandler.handle("auth.user-deletion-progress", message, UserDataPurgedEvent.class,
                envelope -> userDeletionSaga.onPurged(envelope.payload().userId(), envelope.payload().service()));
    }
}
