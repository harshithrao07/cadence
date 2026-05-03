package com.project.cadence.dto;

public record ApiResponseDTO<T>(
        boolean success,
        String message,
        T data
) {
}
