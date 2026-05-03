package com.project.cadence.dto.generic;

import com.project.cadence.dto.artist.ArtistPreviewDTO;
import com.project.cadence.dto.record.RecordPreviewDTO;
import com.project.cadence.dto.song.EachSongDTO;

import java.util.List;

public record DiscoverDTO(
        List<EachSongDTO> trendingSongs,
        List<ArtistPreviewDTO> popularArtists,
        List<EachSongDTO> recommendedSongs,
        List<RecordPreviewDTO> newReleases,
        List<RecordPreviewDTO> newReleasesOfFollowingArtists,
        List<EachSongDTO> recentlyPlayedSongs,
        List<ArtistPreviewDTO> suggestedArtists
) {
}
