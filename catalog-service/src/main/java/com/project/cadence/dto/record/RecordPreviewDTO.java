package com.project.cadence.dto.record;

import com.project.cadence.dto.artist.ArtistPreviewDTO;
import com.project.cadence.model.RecordType;

import java.util.List;

public record RecordPreviewDTO(
        String id,
        String title,
        long releaseTimestamp,
        String coverUrl,
        RecordType recordType,
        List<ArtistPreviewDTO> recordArtists
) {
}
