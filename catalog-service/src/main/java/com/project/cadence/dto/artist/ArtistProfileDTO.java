package com.project.cadence.dto.artist;

import com.project.cadence.dto.record.RecordPreviewDTO;
import com.project.cadence.dto.song.TopSongsInArtistProfileDTO;

import java.util.List;
import java.util.Set;

public record ArtistProfileDTO(
        String id,
        String name,
        String profileUrl,
        String description,
        Integer followers,
        Long monthlyListeners,
        List<TopSongsInArtistProfileDTO> popularSongs,
        Set<RecordPreviewDTO> artistRecords
) {
}
