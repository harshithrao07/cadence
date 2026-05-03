package com.project.cadence.dto.s3;

public record FileUploadResult(
        String tableName,
        String columnName,
        String primaryKey,
        String url
) {
}
