package com.cadence.playlist_service.dto;

import java.util.List;

public record SongPreviewRequestDTO(
        List<String> songIds
) {
}
