package com.cadence.playlist_service.consumers;

import com.cadence.events.MediaUpdatedEvent;
import com.cadence.events.Topics;
import com.cadence.messaging.inbox.IdempotentEventHandler;
import com.cadence.playlist_service.repository.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Applies playlist cover uploads stored by catalog-service. Ownership is checked again here: this service owns
 * the playlist, so it has the final say.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaUpdatedConsumer {
    static final String HANDLER = "playlist.apply-cover";

    private final IdempotentEventHandler eventHandler;
    private final PlaylistRepository playlistRepository;

    @KafkaListener(topics = Topics.MEDIA_UPDATED_TOPIC, groupId = "playlist-service")
    public void handleMediaUpdated(String message) {
        eventHandler.handle(HANDLER, message, MediaUpdatedEvent.class, envelope -> {
            MediaUpdatedEvent media = envelope.payload();
            if (media.target() != MediaUpdatedEvent.Target.PLAYLIST_COVER) {
                return;
            }
            playlistRepository.findById(media.targetId()).ifPresentOrElse(playlist -> {
                if (!media.requestedByAdmin() && !playlist.getOwnerId().equals(media.requestedBy())) {
                    log.warn("Ignoring cover update for playlist {} by non-owner {}", media.targetId(), media.requestedBy());
                    return;
                }
                playlist.setCoverUrl(media.url());
                playlistRepository.save(playlist);
            }, () -> log.warn("Ignoring cover update for missing playlist {}", media.targetId()));
        });
    }
}
