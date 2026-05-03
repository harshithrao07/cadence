package com.cadence.playlist_service.dto;

import com.cadence.playlist_service.model.PlaylistVisibility;

import java.time.Instant;

public record PlaylistPreviewDTO(
        String id,
        String name,
        String coverUrl,
        UserPreviewDTO owner,
        PlaylistVisibility visibility,
        boolean isSystem,
        Instant createdAt,
        Instant updatedAt
) {
}
