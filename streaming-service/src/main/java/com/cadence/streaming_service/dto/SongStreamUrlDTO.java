package com.cadence.streaming_service.dto;

import java.time.Instant;

/**
 * Where the client plays a song from. {@code expiresAt} is null when the URL doesn't expire (audio hosted outside
 * our bucket).
 */
public record SongStreamUrlDTO(
        String url,
        Instant expiresAt
) {
}
