package com.project.cadence.dto.generic;

import com.project.cadence.dto.artist.ArtistPreviewDTO;
import com.project.cadence.dto.playlist.PlaylistPreviewDTO;
import com.project.cadence.dto.record.RecordPreviewDTO;
import com.project.cadence.dto.song.EachSongDTO;

import java.util.List;

public record GlobalSearchDTO(
        List<ArtistPreviewDTO> artists,
        List<RecordPreviewDTO> records,
        List<EachSongDTO> songs,
        List<PlaylistPreviewDTO> playlists
) {
}
