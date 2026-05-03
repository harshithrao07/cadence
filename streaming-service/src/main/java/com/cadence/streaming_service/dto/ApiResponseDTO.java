package com.cadence.streaming_service.dto;

public record ApiResponseDTO<T>(
        boolean success,
        String message,
        T data
) {
}
