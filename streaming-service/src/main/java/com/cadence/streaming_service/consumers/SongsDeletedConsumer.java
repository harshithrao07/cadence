package com.cadence.streaming_service.consumers;

import com.cadence.events.SongsDeletedEvent;
import com.cadence.events.Topics;
import com.cadence.messaging.inbox.IdempotentEventHandler;
import com.cadence.streaming_service.repository.PlayHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Drops play history for songs catalog-service deleted, so they stop taking slots in trending / top-songs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SongsDeletedConsumer {
    static final String HANDLER = "streaming.purge-deleted-songs";

    private final IdempotentEventHandler eventHandler;
    private final PlayHistoryRepository playHistoryRepository;

    @KafkaListener(topics = Topics.SONGS_DELETED_TOPIC, groupId = "streaming-service")
    public void handleSongsDeleted(String message) {
        eventHandler.handle(HANDLER, message, SongsDeletedEvent.class, envelope -> {
            if (envelope.payload().songIds().isEmpty()) {
                return;
            }
            int deleted = playHistoryRepository.deleteBySongIds(envelope.payload().songIds());
            log.info("Removed {} play-history rows for deleted songs of record {}", deleted, envelope.payload().recordId());
        });
    }
}
