package com.project.cadence.dto.artist;

import jakarta.validation.constraints.NotBlank;

import java.util.Optional;

public record UpsertArtistDTO(
        Optional<String> id,
        @NotBlank(message = "Name cannot be empty") String name,
        Optional<String> description
) {
}
