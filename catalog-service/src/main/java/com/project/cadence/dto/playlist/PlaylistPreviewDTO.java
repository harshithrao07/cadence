package com.project.cadence.dto.playlist;

import com.project.cadence.dto.user.UserPreviewDTO;

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
