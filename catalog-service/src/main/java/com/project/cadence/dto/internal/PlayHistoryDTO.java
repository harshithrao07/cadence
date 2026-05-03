package com.project.cadence.dto.internal;

import java.time.Instant;

public record PlayHistoryDTO(
        String userId,
        String songId,
        long playCount,
        Instant createdAt,
        Instant lastPlayedAt
) {
}
