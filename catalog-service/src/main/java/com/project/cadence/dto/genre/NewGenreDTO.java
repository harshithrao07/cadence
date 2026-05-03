package com.project.cadence.dto.genre;

import jakarta.validation.constraints.NotBlank;

public record NewGenreDTO(
        @NotBlank(message = "Genre type cannot be blank") String type
) {
}
