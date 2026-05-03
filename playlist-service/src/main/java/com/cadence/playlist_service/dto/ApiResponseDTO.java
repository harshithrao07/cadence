package com.cadence.playlist_service.dto;

public record ApiResponseDTO<T>(
        boolean success,
        String message,
        T data
) {
}
