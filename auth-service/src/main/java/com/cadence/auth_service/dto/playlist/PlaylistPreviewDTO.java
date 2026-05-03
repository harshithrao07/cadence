package com.cadence.auth_service.dto.playlist;

import com.cadence.auth_service.dto.user.UserPreviewDTO;

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
