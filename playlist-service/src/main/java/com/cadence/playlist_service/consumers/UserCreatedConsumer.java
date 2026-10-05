package com.cadence.playlist_service.consumers;

import com.cadence.events.Topics;
import com.cadence.events.UserCreatedEvent;
import com.cadence.messaging.inbox.IdempotentEventHandler;
import com.cadence.playlist_service.service.PlaylistService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserCreatedConsumer {
    static final String HANDLER = "playlist.create-liked-songs";

    private final IdempotentEventHandler eventHandler;
    private final PlaylistService playlistService;

    @KafkaListener(topics = Topics.USER_CREATED_TOPIC, groupId = "playlist-service")
    public void handleUserCreated(String message) {
        eventHandler.handle(HANDLER, message, UserCreatedEvent.class,
                envelope -> playlistService.createLikedSongsPlaylistForUser(envelope.payload().userId()));
    }
}
