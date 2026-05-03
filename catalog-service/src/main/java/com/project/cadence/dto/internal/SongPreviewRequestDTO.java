package com.project.cadence.dto.internal;

import java.util.List;

public record SongPreviewRequestDTO(
        List<String> songIds
) {
}
