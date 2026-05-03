package com.project.cadence.dto.song;

import com.project.cadence.dto.artist.ArtistPreviewDTO;
import com.project.cadence.dto.genre.GenrePreviewDTO;
import com.project.cadence.dto.record.RecordPreviewDTO;
import com.project.cadence.dto.record.RecordPreviewWithCoverImageDTO;

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
