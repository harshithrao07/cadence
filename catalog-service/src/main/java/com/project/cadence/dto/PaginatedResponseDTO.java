package com.project.cadence.dto;

import java.util.List;

public record PaginatedResponseDTO<T>(
        List<T> content,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages,
        Boolean last
) {
}
