package com.project.cadence.dto.s3;

import com.amazonaws.HttpMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MetadataDTO(
        @NotBlank(message = "Category cannot be blank") String category,
        @NotBlank(message = "Sub-category cannot be blank") String subCategory,
        @NotBlank(message = "Primary Key cannot be blank") String primaryKey,
        @NotNull(message = "Request Method cannot be null") HttpMethod httpMethod
) {
}
