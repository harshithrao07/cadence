package com.cadence.streaming_service.client;

import com.cadence.streaming_service.dto.SongStreamingMetadataDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback when catalog-service is unreachable for song-streaming metadata.
 *
 * <p>Returns {@code null} — StreamingService.streamSongById already maps a null
 * metadata to 404, so the user sees "song not found" instead of a 500 / hang.
 */
@Slf4j
@Component
public class CatalogClientFallback implements CatalogClient {

    @Override
    public SongStreamingMetadataDTO getStreamingMetadata(String songId) {
        log.warn("Circuit breaker open for catalog-service.getStreamingMetadata(songId={}); returning null", songId);
        return null;
    }
}
