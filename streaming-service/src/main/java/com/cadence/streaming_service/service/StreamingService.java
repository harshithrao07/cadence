package com.cadence.streaming_service.service;

import com.cadence.streaming_service.client.CatalogClient;
import com.cadence.streaming_service.dto.SongStreamUrlDTO;
import com.cadence.streaming_service.dto.SongStreamingMetadataDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingService {
    /** Long enough to play a song through, with pauses; the browser fetches byte ranges with this URL until then. */
    static final Duration URL_TTL = Duration.ofHours(1);

    private final CatalogClient catalogClient;
    private final AwsStreamingService awsStreamingService;
    private final WorkerService workerService;

    /**
     * The URL the client plays the song from, recording one play. Empty if the song or its audio doesn't exist.
     */
    public Optional<SongStreamUrlDTO> getStreamUrl(String songId, String userId) {
        SongStreamingMetadataDTO metadata = catalogClient.getStreamingMetadata(songId);
        if (metadata == null || metadata.songUrl() == null || metadata.songUrl().isBlank()) {
            return Optional.empty();
        }

        Optional<SongStreamUrlDTO> streamUrl = resolve(metadata.songUrl());
        if (streamUrl.isEmpty()) {
            log.warn("Audio for song {} is missing from the bucket", songId);
            return Optional.empty();
        }

        workerService.recordPlay(userId, songId);
        return streamUrl;
    }

    private Optional<SongStreamUrlDTO> resolve(String songUrl) {
        Optional<String> objectKey = awsStreamingService.bucketObjectKey(songUrl);
        if (objectKey.isEmpty()) {
            // Hosted elsewhere (e.g. seeded tracks): the client plays it directly.
            return Optional.of(new SongStreamUrlDTO(songUrl, null));
        }
        // song_url is set before the client's upload, which may never have happened.
        if (!awsStreamingService.findByName(objectKey.get())) {
            return Optional.empty();
        }
        Instant expiresAt = Instant.now().plus(URL_TTL);
        return Optional.of(new SongStreamUrlDTO(
                awsStreamingService.generatePresignedGetUrl(objectKey.get(), expiresAt),
                expiresAt
        ));
    }
}
