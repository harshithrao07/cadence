package com.project.cadence.service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.project.cadence.constant.UploadTarget;
import com.project.cadence.dto.s3.FileUploadResult;
import com.project.cadence.dto.s3.MetadataDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;



@Slf4j
@Service
@RequiredArgsConstructor
public class AwsService {

    private final AmazonS3 amazonS3;
    private final MediaTargetWriter mediaTargetWriter;
    private final UploadAuthorizer uploadAuthorizer;

    @Value("${cloud.aws.s3.bucket}")
    private String s3BucketName;

    public String generateUrl(String fileName, HttpMethod httpMethod) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.MINUTE, 15); // Generated URL will be valid for 15 minutes
        return amazonS3.generatePresignedUrl(s3BucketName, fileName, calendar.getTime(), httpMethod).toString();
    }

    public boolean findByName(String fileName) {
        return amazonS3.doesObjectExist(s3BucketName, fileName);
    }

    /**
     * Presigned URL requested by a client: the target must be allow-listed and the caller allowed to modify it.
     */
    public ResponseEntity<String> getPresignedUrlForClient(MetadataDTO metadata, String userId, boolean isAdmin) {
        Optional<UploadTarget> target = UploadTarget.of(metadata.category(), metadata.subCategory());
        if (target.isEmpty() || !UploadAuthorizer.isValidPrimaryKey(metadata.primaryKey())) {
            return ResponseEntity.badRequest().build();
        }
        if (!uploadAuthorizer.mayModify(target.get(), metadata.primaryKey(), userId, isAdmin)) {
            log.warn("User {} denied presigned {} for {} {}", userId, metadata.httpMethod(), target.get(), metadata.primaryKey());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(presign(target.get(), metadata.primaryKey(), metadata.httpMethod(), userId, isAdmin));
    }

    /**
     * For trusted callers inside catalog-service (no authorization; acts as an admin). The target must still be
     * allow-listed.
     */
    public String getPresignedUrl(String category, String subCategory, String primaryKey, HttpMethod httpMethod) {
        UploadTarget target = UploadTarget.of(category, subCategory)
                .orElseThrow(() -> new IllegalArgumentException("Unknown upload target: " + category + " " + subCategory));
        return presign(target, primaryKey, httpMethod, null, true);
    }

    private String presign(UploadTarget target, String primaryKey, HttpMethod httpMethod, String userId, boolean isAdmin) {
        String fileName = target.objectKey(primaryKey);
        log.info("Generated file name '{}' for saving in bucket '{}'", fileName, s3BucketName);

        if (httpMethod.equals(HttpMethod.DELETE)) {
            handleDeleteDbUpdate(target, primaryKey, userId, isAdmin);
        }

        return generateUrl(fileName, httpMethod);
    }

    public void handleDeleteDbUpdate(UploadTarget target, String primaryKey, String userId, boolean isAdmin) {
        try {
            mediaTargetWriter.write(target, primaryKey, null, userId, isAdmin);
        } catch (Exception e) {
            log.error("DB update failed for delete. target={}, id={}", target, primaryKey, e);
        }
    }

    public void deleteObject(String fileName) {
        try {
            amazonS3.deleteObject(s3BucketName, fileName);
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
        }
    }

    public S3Object getObject(String fileName) {
        return amazonS3.getObject(s3BucketName, fileName);
    }

    /**
     * Uploads files named "table column primaryKey". Every part is validated and authorized before anything is
     * uploaded: an unknown target or bad id is a 400, a target the caller may not modify is a 403.
     */
    public ResponseEntity<List<FileUploadResult>> save(String userId, boolean isAdmin, List<Part> parts) {
        try {
            List<UploadRequest> requests = new ArrayList<>();
            for (Part part : parts) {
                Optional<UploadRequest> request = UploadRequest.parse(part);
                if (request.isEmpty()) {
                    return ResponseEntity.badRequest().build();
                }
                if (!uploadAuthorizer.mayModify(request.get().target(), request.get().primaryKey(), userId, isAdmin)) {
                    log.warn("User {} denied upload to {} {}", userId, request.get().target(), request.get().primaryKey());
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
                requests.add(request.get());
            }

            List<CompletableFuture<FileUploadResult>> futures = new ArrayList<>();
            for (UploadRequest request : requests) {
                futures.add(uploadFileAsync(request.target(), request.primaryKey(), request.part()));
            }

            // Wait for all uploads to complete
            List<FileUploadResult> uploadResults = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .toList();

            for (FileUploadResult result : uploadResults) {
                UploadTarget target = UploadTarget.of(result.tableName(), result.columnName()).orElseThrow();
                mediaTargetWriter.write(target, result.primaryKey(), result.url(), userId, isAdmin);
                log.info("Recorded {} for pk '{}' with URL '{}'", target, result.primaryKey(), result.url());
            }

            return new ResponseEntity<>(uploadResults, HttpStatus.OK);
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
        }
        return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Async
    public CompletableFuture<FileUploadResult> uploadFileAsync(UploadTarget target, String primaryKey, Part part) {
        try {
            String objectKey = target.objectKey(primaryKey);

            /* --------- CHECK & DELETE EXISTING FILE --------- */
            if (amazonS3.doesObjectExist(s3BucketName, objectKey)) {
                amazonS3.deleteObject(s3BucketName, objectKey);
                log.info("Existing file deleted: {}/{}", s3BucketName, objectKey);
            }

            // Delete case
            if (part.getSize() == 0) {
                return CompletableFuture.completedFuture(
                        new FileUploadResult(target.table(), target.column(), primaryKey, null)
                );
            }

            /* --------- UPLOAD NEW FILE --------- */
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(part.getSize());
            metadata.setContentType(part.getContentType());

            amazonS3.putObject(
                    s3BucketName,
                    objectKey,
                    part.getInputStream(),
                    metadata
            );

            log.info("File uploaded successfully: {}/{}", s3BucketName, objectKey);

            return CompletableFuture.completedFuture(
                    new FileUploadResult(target.table(), target.column(), primaryKey, getUrl(objectKey))
            );

        } catch (Exception e) {
            log.error("Error uploading file", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private record UploadRequest(UploadTarget target, String primaryKey, Part part) {
        /** Parses a part named "table column primaryKey"; empty if the target isn't allow-listed or the id is invalid. */
        static Optional<UploadRequest> parse(Part part) {
            String name = part.getSubmittedFileName();
            String[] nameParts = name == null ? new String[0] : name.split(" ");
            if (nameParts.length != 3 || !UploadAuthorizer.isValidPrimaryKey(nameParts[2])) {
                return Optional.empty();
            }
            return UploadTarget.of(nameParts[0], nameParts[1]).map(t -> new UploadRequest(t, nameParts[2], part));
        }
    }

    public String extractKeyFromUrl(String songUrl) {
        try {
            URI uri = new URI(songUrl);
            return uri.getPath().substring(1); // removes leading "/"
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid song URL", e);
        }
    }

    public String getUrl(String objectKey) {
        return amazonS3.getUrl(s3BucketName, objectKey).toString();
    }

    public S3Object getObjectWithRange(String key, long start, long end) {
        GetObjectRequest request = new GetObjectRequest(s3BucketName, key);
        request.setRange(start, end);
        return amazonS3.getObject(request);
    }

    public long getFileSize(String key) {
        ObjectMetadata metadata = amazonS3.getObjectMetadata(s3BucketName, key);
        return metadata.getContentLength();
    }

}
