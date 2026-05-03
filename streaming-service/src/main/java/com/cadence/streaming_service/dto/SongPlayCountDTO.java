package com.cadence.streaming_service.dto;

public record SongPlayCountDTO(
        String songId,
        long playCount
) {
}
