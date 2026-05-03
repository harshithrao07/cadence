package com.cadence.streaming_service.dto;

public record SongPlayStatsDTO(
        String songId,
        long totalPlays,
        long totalListeners
) {
}
