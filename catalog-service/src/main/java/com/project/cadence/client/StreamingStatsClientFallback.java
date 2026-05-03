package com.project.cadence.client;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.internal.PlayHistoryDTO;
import com.project.cadence.dto.internal.SongPlayCountDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Fallback when streaming-service is down — discover feed renders the sections that
 * don't depend on play stats (popular artists, new releases) and skips the trending /
 * recently-played / top-songs sections by treating them as empty.
 */
@Slf4j
@Component
public class StreamingStatsClientFallback implements StreamingStatsClient {

    @Override
    public ApiResponseDTO<List<SongPlayCountDTO>> getTrendingSongsResponse(Instant since, int page, int size) {
        log.warn("Circuit breaker open for streaming-service.getTrendingSongs; returning empty");
        return new ApiResponseDTO<>(false, "streaming-service unavailable", List.of());
    }

    @Override
    public ApiResponseDTO<List<PlayHistoryDTO>> getRecentHistoryResponse(String userId, int page, int size) {
        log.warn("Circuit breaker open for streaming-service.getRecentHistory(userId={}); returning empty", userId);
        return new ApiResponseDTO<>(false, "streaming-service unavailable", List.of());
    }

    @Override
    public ApiResponseDTO<List<PlayHistoryDTO>> getUserTopSongsResponse(String userId, int page, int size) {
        log.warn("Circuit breaker open for streaming-service.getUserTopSongs(userId={}); returning empty", userId);
        return new ApiResponseDTO<>(false, "streaming-service unavailable", List.of());
    }
}
