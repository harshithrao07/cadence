package com.project.cadence.dto.song;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record UpsertSongDTO(
        Optional<String> id,
        @NotBlank(message = "Song title cannot be empty") String title,
        @NotEmpty(message = "Genres cannot be empty") Set<String> genreIds,
        @NotEmpty(message = "Artists cannot be empty") List<String> artistIds,
        @NotNull(message = "Total Duration of song cannot be null") int totalDuration
) {
}
