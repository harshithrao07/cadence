package com.cadence.playlist_service.dto;

import com.cadence.playlist_service.model.PlaylistVisibility;
import jakarta.validation.constraints.NotBlank;

import java.util.Optional;

public record UpsertPlaylistDTO(
        Optional<String> id,
        @NotBlank(message = "Playlist name cannot be empty") String name,
        Optional<PlaylistVisibility> visibility
) {
}
