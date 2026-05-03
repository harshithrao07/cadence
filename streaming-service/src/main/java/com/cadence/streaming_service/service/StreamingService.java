package com.cadence.streaming_service.service;

import com.amazonaws.services.s3.model.S3Object;
import com.cadence.streaming_service.client.CatalogClient;
import com.cadence.streaming_service.dto.SongStreamingMetadataDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingService {
    private final CatalogClient catalogClient;
    private final AwsStreamingService awsStreamingService;
    private final WorkerService workerService;

    public ResponseEntity<StreamingResponseBody> streamSongById(
            String songId,
            String userId,
            String rangeHeader
    ) {
        try {
            SongStreamingMetadataDTO metadata = catalogClient.getStreamingMetadata(songId);
            if (metadata == null || metadata.songUrl() == null || metadata.songUrl().isBlank()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            workerService.recordPlay(userId, songId);

            String songUrl = metadata.songUrl();
            String objectKey = awsStreamingService.extractKeyFromUrl(songUrl);

            if (objectKey != null && !objectKey.isEmpty() && awsStreamingService.findByName(objectKey)) {
                return streamFromS3(objectKey, rangeHeader);
            }

            return streamFromExternalUrl(songUrl, rangeHeader);
        } catch (Exception e) {
            log.error("Streaming error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private ResponseEntity<StreamingResponseBody> streamFromS3(String objectKey, String rangeHeader) {
        long fileSize = awsStreamingService.getFileSize(objectKey);
        Range range = parseRange(rangeHeader, fileSize);

        S3Object s3Object = awsStreamingService.getObjectWithRange(objectKey, range.start(), range.end());

        StreamingResponseBody stream = outputStream -> {
            try (InputStream in = s3Object.getObjectContent()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE,
                s3Object.getObjectMetadata().getContentType() != null
                        ? s3Object.getObjectMetadata().getContentType()
                        : "audio/mpeg");
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + range.start() + "-" + range.end() + "/" + fileSize);
        headers.setContentLength(range.contentLength());

        return new ResponseEntity<>(stream, headers, HttpStatus.PARTIAL_CONTENT);
    }

    private ResponseEntity<StreamingResponseBody> streamFromExternalUrl(String songUrl, String rangeHeader) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(songUrl).openConnection();
        connection.setRequestMethod("GET");

        if (rangeHeader != null) {
            connection.setRequestProperty("Range", rangeHeader);
        }

        connection.connect();

        int responseCode = connection.getResponseCode();
        long fileSize = connection.getContentLengthLong();
        Range range = parseRange(rangeHeader, fileSize);

        StreamingResponseBody stream = outputStream -> {
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE,
                connection.getContentType() != null
                        ? connection.getContentType()
                        : "audio/mpeg");
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

        if (responseCode == 206) {
            headers.set(HttpHeaders.CONTENT_RANGE, connection.getHeaderField("Content-Range"));
            headers.setContentLength(range.contentLength());
            return new ResponseEntity<>(stream, headers, HttpStatus.PARTIAL_CONTENT);
        }

        headers.setContentLength(fileSize);
        return new ResponseEntity<>(stream, headers, HttpStatus.OK);
    }

    private Range parseRange(String rangeHeader, long fileSize) {
        long start = 0;
        long end = fileSize - 1;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String rangeValue = rangeHeader.substring(6).trim();
            String[] ranges = rangeValue.split("-");

            if (!ranges[0].isEmpty()) {
                start = Long.parseLong(ranges[0]);
            }

            if (ranges.length > 1 && !ranges[1].isEmpty()) {
                end = Long.parseLong(ranges[1]);
            } else {
                end = fileSize - 1;
            }

            if (end >= fileSize) {
                end = fileSize - 1;
            }

            if (start > end) {
                start = 0;
                end = fileSize - 1;
            }
        }

        return new Range(start, end);
    }

    private record Range(long start, long end) {
        long contentLength() {
            return end - start + 1;
        }
    }
}
