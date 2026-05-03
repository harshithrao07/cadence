package com.project.cadence.dto.song;

import com.project.cadence.dto.artist.ArtistPreviewDTO;

import java.util.ArrayList;
import java.util.List;

public record TopSongsInArtistProfileDTO(
        String id,
        String title,
        Integer totalDuration,
        String coverUrl,
        Long totalPlays,
        String recordId,
        String recordTitle,
        List<ArtistPreviewDTO> artists
) {
    // JPQL-friendly constructor
    public TopSongsInArtistProfileDTO(
            String id,
            String title,
            Integer totalDuration,
            String coverUrl,
            Long totalPlays,
            String recordId,
            String recordTitle
    ) {
        this(
                id,
                title,
                totalDuration,
                coverUrl,
                totalPlays,
                recordId,
                recordTitle,
                new ArrayList<>()
        );
    }
}
