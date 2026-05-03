package com.project.cadence.service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.project.cadence.dto.s3.FileUploadResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.project.cadence.constant.AdminOnlyUploadTables.TABLES;


@Slf4j
@Service
@RequiredArgsConstructor
public class AwsService {

    private final AmazonS3 amazonS3;
    private final JdbcTemplate jdbcTemplate;

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

    public String getPresignedUrl(String category, String subCategory, String primaryKey, HttpMethod httpMethod) {
        String fileName = category + "/" + subCategory + "/" + primaryKey;
        log.info("Generated file name '{}' for saving in bucket '{}'", fileName, s3BucketName);

        if (httpMethod.equals(HttpMethod.DELETE)) {
            handleDeleteDbUpdate(category, subCategory, primaryKey);
        }

        return generateUrl(fileName, httpMethod);
    }

    @Transactional
    public void handleDeleteDbUpdate(
            String category,
            String subCategory,
            String primaryKey
    ) {
        try {
            String sql = "UPDATE " + category +
                    " SET " + subCategory + " = NULL WHERE id = ?";

            jdbcTemplate.update(sql, primaryKey);
        } catch (Exception e) {
            log.error(
                    "DB update failed for delete. table={}, column={}, id={}",
                    category, subCategory, primaryKey, e
            );
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

    public ResponseEntity<List<FileUploadResult>> save(boolean isAdmin, List<Part> parts) {
        try {
            List<CompletableFuture<FileUploadResult>> futures = new ArrayList<>();

            for (Part part : parts) {
                CompletableFuture<FileUploadResult> future = uploadFileAsync(isAdmin, part);
                if (future == null) {
                    return null;
                }
                futures.add(future); // async call returns CompletableFuture
            }

            // Wait for all uploads to complete
            List<FileUploadResult> uploadResults = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .toList();

            for (FileUploadResult result : uploadResults) {
                // Dynamic SQL to update the column with the file URL
                String sql = String.format(
                        "UPDATE %s SET %s = ? WHERE id = ?",
                        result.tableName(),
                        result.columnName()
                );

                jdbcTemplate.update(sql, result.url(), result.primaryKey());
                log.info("Updated table '{}' column '{}' for pk '{}' with URL '{}'",
                        result.tableName(),
                        result.columnName(),
                        result.primaryKey(),
                        result.url());
            }

            return new ResponseEntity<>(uploadResults, HttpStatus.OK);
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
        }
        return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Async
    public CompletableFuture<FileUploadResult> uploadFileAsync(boolean isAdmin, Part part) {
        try {
            long size = part.getSize();
            String submittedName = part.getSubmittedFileName();
            String[] nameParts = submittedName.split(" ");

            if (nameParts.length != 3) {
                throw new IllegalArgumentException("Invalid file naming format");
            }

            String tableName = nameParts[0];
            String columnName = nameParts[1];
            String primaryKey = nameParts[2];

            String objectKey = tableName + "/" + columnName + "/" + primaryKey;

            if (TABLES.contains(tableName) && !isAdmin) {
                return CompletableFuture.failedFuture(
                        new AccessDeniedException("Admin access required")
                );
            }

            /* --------- CHECK & DELETE EXISTING FILE --------- */
            if (amazonS3.doesObjectExist(s3BucketName, objectKey)) {
                amazonS3.deleteObject(s3BucketName, objectKey);
                log.info("Existing file deleted: {}/{}", s3BucketName, objectKey);
            }

            // Delete case
            if (size == 0) {
                return CompletableFuture.completedFuture(
                        new FileUploadResult(
                                tableName,
                                columnName,
                                primaryKey,
                                null
                        )
                );
            }

            /* --------- UPLOAD NEW FILE --------- */
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(part.getSize());
            metadata.setContentType(part.getContentType());

            System.out.println("Uploading: " + objectKey);
            amazonS3.putObject(
                    s3BucketName,
                    objectKey,
                    part.getInputStream(),
                    metadata
            );

            log.info("File uploaded successfully: {}/{}", s3BucketName, objectKey);

            String fileUrl = getUrl(objectKey);

            return CompletableFuture.completedFuture(
                    new FileUploadResult(
                            tableName,
                            columnName,
                            primaryKey,
                            fileUrl
                    )
            );

        } catch (Exception e) {
            log.error("Error uploading file", e);
            return CompletableFuture.failedFuture(e);
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
