package com.project.cadence.dto.song;

public record SongBaseDTO(
        String id,
        String title,
        Integer totalDuration,
        String recordId,
        String recordTitle,
        String coverUrl
) {}

