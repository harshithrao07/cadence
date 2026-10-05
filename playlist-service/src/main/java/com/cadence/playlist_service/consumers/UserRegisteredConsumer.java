package com.cadence.playlist_service.consumers;

import com.cadence.events.LikedSongsCreatedEvent;
import com.cadence.events.LikedSongsFailedEvent;
import com.cadence.events.Topics;
import com.cadence.events.UserRegisteredEvent;
import com.cadence.messaging.inbox.IdempotentEventHandler;
import com.cadence.messaging.outbox.OutboxPublisher;
import com.cadence.playlist_service.service.PlaylistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Registration saga participant: provisions the new user's Liked Songs playlist and replies to auth-service in the
 * same transaction. An invalid request gets a failure reply (auth compensates); infrastructure errors are retried
 * and then dead-lettered, in which case auth's timeout sweeper fails the registration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredConsumer {
    static final String HANDLER = "playlist.create-liked-songs";

    private final IdempotentEventHandler eventHandler;
    private final PlaylistService playlistService;
    private final OutboxPublisher outboxPublisher;

    @KafkaListener(topics = Topics.USER_REGISTERED_TOPIC, groupId = "playlist-service")
    public void handleUserRegistered(String message) {
        eventHandler.handle(HANDLER, message, UserRegisteredEvent.class, envelope -> {
            String userId = envelope.payload().userId();
            if (userId == null || userId.isBlank()) {
                log.warn("Rejecting registration without a userId (eventId={})", envelope.eventId());
                outboxPublisher.publish(Topics.LIKED_SONGS_FAILED_TOPIC, "user", String.valueOf(userId),
                        new LikedSongsFailedEvent(userId, "missing userId"));
                return;
            }
            String playlistId = playlistService.createLikedSongsPlaylistForUser(userId);
            outboxPublisher.publish(Topics.LIKED_SONGS_CREATED_TOPIC, "user", userId,
                    new LikedSongsCreatedEvent(userId, playlistId));
        });
    }
}
