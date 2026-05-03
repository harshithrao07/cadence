package com.project.cadence.client;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.playlist.PlaylistPreviewDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fallback when playlist-service is down — search returns no playlists rather than
 * the entire {@code /api/v1/search} endpoint failing. Existing default methods on the
 * interface unwrap a {@code success=false} response into an empty list automatically.
 */
@Slf4j
@Component
public class PlaylistClientFallback implements PlaylistClient {

    @Override
    public ApiResponseDTO<List<PlaylistPreviewDTO>> searchPlaylistsResponse(String key, int page, int size) {
        log.warn("Circuit breaker open for playlist-service.searchPlaylists(key={}); returning empty", key);
        return new ApiResponseDTO<>(false, "playlist-service unavailable", List.of());
    }
}
