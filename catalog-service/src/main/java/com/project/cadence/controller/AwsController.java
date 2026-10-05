package com.project.cadence.controller;

import com.project.cadence.dto.s3.FileUploadResult;
import com.project.cadence.dto.s3.MetadataDTO;
import com.project.cadence.service.AwsService;
import jakarta.servlet.http.Part;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/files")
public class AwsController {
    private final AwsService awsService;

    @PostMapping("/presigned-url")
    public ResponseEntity<String> getPresignedUrl(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String userRole,
            @Valid @RequestBody MetadataDTO metadataDTO
    ) {
        return awsService.getPresignedUrlForClient(metadataDTO, userId, isAdmin(userRole));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<FileUploadResult>> save(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String userRole,
            @RequestPart("file") List<Part> parts
    ) {
        return awsService.save(userId, isAdmin(userRole), parts);
    }

    private static boolean isAdmin(String userRole) {
        return "ADMIN".equalsIgnoreCase(userRole) || "ROLE_ADMIN".equalsIgnoreCase(userRole);
    }
}
