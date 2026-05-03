package com.cadence.playlist_service.dto;

import java.util.List;

public record EachSongDTO(
        String id,
        String title,
        Integer totalDuration,
        List<ArtistPreviewDTO> artists,
        List<GenrePreviewDTO> genres,
        RecordPreviewWithCoverImageDTO recordPreviewWithCoverImageDTO
) {
}
