package com.cadence.streaming_service.service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AwsStreamingService {
    private final AmazonS3 amazonS3;

    @Value("${cloud.aws.s3.bucket}")
    private String s3BucketName;

    public boolean findByName(String fileName) {
        return amazonS3.doesObjectExist(s3BucketName, fileName);
    }

    public String extractKeyFromUrl(String songUrl) {
        try {
            URI uri = new URI(songUrl);
            return uri.getPath().substring(1);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid song URL", e);
        }
    }

    /** The object key if songUrl is an object URL in our bucket (as catalog-service stores it); empty otherwise. */
    public Optional<String> bucketObjectKey(String songUrl) {
        try {
            String key = extractKeyFromUrl(songUrl);
            if (key.isEmpty() || !amazonS3.getUrl(s3BucketName, key).toString().equals(songUrl)) {
                return Optional.empty();
            }
            return Optional.of(key);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public String generatePresignedGetUrl(String key, Instant expiresAt) {
        return amazonS3.generatePresignedUrl(s3BucketName, key, Date.from(expiresAt), HttpMethod.GET).toString();
    }
}
