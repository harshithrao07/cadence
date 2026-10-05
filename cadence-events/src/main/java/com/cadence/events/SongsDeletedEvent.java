package com.cadence.events;

import java.util.List;

/**
 * Songs catalog-service has deleted (a deleted record, or songs dropped when a record was edited). Services holding
 * song ids (playlist-service's playlists, streaming-service's play history) purge them. Forward-only: nothing to
 * compensate, consumers retry until they succeed.
 */
public record SongsDeletedEvent(String recordId, List<String> songIds) {
    public SongsDeletedEvent {
        songIds = songIds == null ? List.of() : List.copyOf(songIds);
    }
}
