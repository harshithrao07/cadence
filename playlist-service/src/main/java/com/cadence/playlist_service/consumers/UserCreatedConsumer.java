package com.cadence.playlist_service.consumers;

import com.cadence.playlist_service.dto.Topics;
import com.cadence.playlist_service.events.UserCreatedEvent;
import com.cadence.playlist_service.service.PlaylistService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserCreatedConsumer {
    private final PlaylistService playlistService;

    @KafkaListener(topics = Topics.USER_CREATED_TOPIC, groupId = "playlist-service")
    public void handleUserCreated(UserCreatedEvent event) {
        playlistService.createLikedSongsPlaylistForUser(event.getUserId());
    }
}
