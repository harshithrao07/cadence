package com.project.cadence.producers;

import com.cadence.events.SongsDeletedEvent;
import com.cadence.events.Topics;
import com.cadence.messaging.outbox.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * Tells other services which song ids no longer exist. Call in the transaction that deletes the songs.
 */
@Service
@RequiredArgsConstructor
public class SongsDeletedProducer {
    private final OutboxPublisher outboxPublisher;

    public void send(String recordId, Collection<String> songIds) {
        if (songIds.isEmpty()) {
            return;
        }
        outboxPublisher.publish(Topics.SONGS_DELETED_TOPIC, "record", recordId,
                new SongsDeletedEvent(recordId, List.copyOf(songIds)));
    }
}
