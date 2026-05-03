package com.project.cadence.dto.record;

public record RecordPreviewWithCoverImageDTO(
        String id,
        String title,
        String coverUrl,
        long releaseTimestamp
) {
}
