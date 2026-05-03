package com.project.cadence.dto.record;

import com.project.cadence.dto.song.UpsertSongDTO;
import com.project.cadence.model.RecordType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Optional;

public record UpsertRecordDTO(
        Optional<String> id,
        @NotBlank(message = "Record title cannot be empty") String title,
        @NotNull(message = "Release Timestamp cannot be null") Long releaseTimestamp,
        @NotNull(message = "Record Type cannot be null") RecordType recordType,
        @NotEmpty(message = "Artist Id's cannot be empty") List<String> artistIds,
        @NotEmpty(message = "Songs for a record cannot be empty") List<UpsertSongDTO> songs
) {
}
