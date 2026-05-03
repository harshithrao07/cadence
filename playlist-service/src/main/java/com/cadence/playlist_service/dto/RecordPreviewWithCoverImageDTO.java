package com.cadence.playlist_service.dto;

public record RecordPreviewWithCoverImageDTO(
        String id,
        String title,
        String coverUrl,
        long releaseTimestamp
) {
}
