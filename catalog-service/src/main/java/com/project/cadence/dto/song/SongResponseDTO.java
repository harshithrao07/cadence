package com.project.cadence.dto.song;

public record SongResponseDTO(
        String id,
        String title,
        String presignedUrl
) {
}
