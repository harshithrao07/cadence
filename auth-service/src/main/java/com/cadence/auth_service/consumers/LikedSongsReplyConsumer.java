package com.cadence.auth_service.consumers;

import com.cadence.auth_service.saga.RegistrationSaga;
import com.cadence.events.LikedSongsCreatedEvent;
import com.cadence.events.LikedSongsFailedEvent;
import com.cadence.events.Topics;
import com.cadence.messaging.inbox.IdempotentEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * playlist-service's replies in the registration saga.
 */
@Component
@RequiredArgsConstructor
public class LikedSongsReplyConsumer {
    private final IdempotentEventHandler eventHandler;
    private final RegistrationSaga registrationSaga;

    @KafkaListener(topics = Topics.LIKED_SONGS_CREATED_TOPIC, groupId = "auth-service")
    public void handleCreated(String message) {
        eventHandler.handle("auth.registration-activate", message, LikedSongsCreatedEvent.class,
                envelope -> registrationSaga.onLikedSongsCreated(envelope.payload().userId()));
    }

    @KafkaListener(topics = Topics.LIKED_SONGS_FAILED_TOPIC, groupId = "auth-service")
    public void handleFailed(String message) {
        eventHandler.handle("auth.registration-fail", message, LikedSongsFailedEvent.class,
                envelope -> registrationSaga.onLikedSongsFailed(envelope.payload().userId(), envelope.payload().reason()));
    }
}
