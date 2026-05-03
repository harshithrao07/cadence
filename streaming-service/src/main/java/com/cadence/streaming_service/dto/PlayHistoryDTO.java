package com.cadence.streaming_service.dto;

import java.time.Instant;

public record PlayHistoryDTO(
        String userId,
        String songId,
        long playCount,
        Instant createdAt,
        Instant lastPlayedAt
) {
}
