package com.cadence.streaming_service.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;

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
