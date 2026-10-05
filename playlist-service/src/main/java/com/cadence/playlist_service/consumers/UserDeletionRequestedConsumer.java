package com.cadence.playlist_service.consumers;

import com.cadence.events.Topics;
import com.cadence.events.UserDataPurgedEvent;
import com.cadence.events.UserDeletionRequestedEvent;
import com.cadence.messaging.inbox.IdempotentEventHandler;
import com.cadence.messaging.outbox.OutboxPublisher;
import com.cadence.playlist_service.model.Playlist;
import com.cadence.playlist_service.repository.LikedPlaylistRepository;
import com.cadence.playlist_service.repository.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Account deletion saga participant: removes the user's playlists (Liked Songs included), the user's likes, and other
 * users' likes of the deleted playlists, then confirms to auth-service in the same transaction. Idempotent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletionRequestedConsumer {
    static final String HANDLER = "playlist.purge-user";

    private final IdempotentEventHandler eventHandler;
    private final PlaylistRepository playlistRepository;
    private final LikedPlaylistRepository likedPlaylistRepository;
    private final OutboxPublisher outboxPublisher;

    @KafkaListener(topics = Topics.USER_DELETION_REQUESTED_TOPIC, groupId = "playlist-service")
    public void handleUserDeletionRequested(String message) {
        eventHandler.handle(HANDLER, message, UserDeletionRequestedEvent.class, envelope -> {
            String userId = envelope.payload().userId();
            List<Playlist> owned = playlistRepository.findByOwnerId(userId);
            List<String> ownedIds = owned.stream().map(Playlist::getId).toList();

            int ownLikes = likedPlaylistRepository.deleteAllByUserId(userId);
            int othersLikes = ownedIds.isEmpty() ? 0 : likedPlaylistRepository.deleteAllByPlaylistIdIn(ownedIds);
            playlistRepository.deleteAll(owned);

            outboxPublisher.publish(Topics.PLAYLIST_USER_DATA_PURGED_TOPIC, "user", userId,
                    new UserDataPurgedEvent(userId, "playlist"));
            log.info("Purged user {}: {} playlists, {} own likes, {} likes of their playlists",
                    userId, owned.size(), ownLikes, othersLikes);
        });
    }
}
