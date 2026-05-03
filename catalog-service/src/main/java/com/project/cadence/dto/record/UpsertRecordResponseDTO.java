package com.project.cadence.dto.record;

import com.project.cadence.dto.song.SongResponseDTO;

import java.util.List;

public record UpsertRecordResponseDTO(
        String id,
        List<SongResponseDTO> songs
) {
}

