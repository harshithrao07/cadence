package com.cadence.playlist_service.consumers;

import com.cadence.events.SongsDeletedEvent;
import com.cadence.events.Topics;
import com.cadence.messaging.inbox.IdempotentEventHandler;
import com.cadence.playlist_service.model.Playlist;
import com.cadence.playlist_service.repository.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes songs catalog-service deleted from every playlist. Goes through the entity (not a bulk DELETE) so the
 * song_order of the remaining songs is renumbered without gaps. The list is replaced rather than edited in place:
 * in-place removal makes Hibernate shift rows one UPDATE at a time, which transiently duplicates a
 * (playlist_id, song_id) pair and violates its unique constraint.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SongsDeletedConsumer {
    static final String HANDLER = "playlist.purge-deleted-songs";

    private final IdempotentEventHandler eventHandler;
    private final PlaylistRepository playlistRepository;

    @KafkaListener(topics = Topics.SONGS_DELETED_TOPIC, groupId = "playlist-service")
    public void handleSongsDeleted(String message) {
        eventHandler.handle(HANDLER, message, SongsDeletedEvent.class, envelope -> {
            Set<String> deleted = new HashSet<>(envelope.payload().songIds());
            if (deleted.isEmpty()) {
                return;
            }
            List<Playlist> affected = playlistRepository.findAllContainingAnySong(deleted);
            affected.forEach(playlist -> playlist.setSongIds(new ArrayList<>(
                    playlist.getSongIds().stream().filter(id -> !deleted.contains(id)).toList())));
            playlistRepository.saveAll(affected);
            log.info("Removed {} deleted songs of record {} from {} playlists",
                    deleted.size(), envelope.payload().recordId(), affected.size());
        });
    }
}
