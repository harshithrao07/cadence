package com.cadence.auth_service.client;

import com.cadence.auth_service.dto.ApiResponseDTO;
import com.cadence.auth_service.dto.playlist.PlaylistPreviewDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fallback when playlist-service is down — user-profile endpoints return without
 * playlist sections rather than failing the whole profile fetch.
 */
@Slf4j
@Component
public class PlaylistClientFallback implements PlaylistClient {

    @Override
    public ApiResponseDTO<List<PlaylistPreviewDTO>> searchPlaylistsResponse(String key, int page, int size) {
        log.warn("Circuit breaker open for playlist-service.searchPlaylists(key={}); returning empty", key);
        return new ApiResponseDTO<>(false, "playlist-service unavailable", List.of());
    }

    @Override
    public ApiResponseDTO<List<PlaylistPreviewDTO>> getCreatedPlaylistsResponse(String userId, boolean includePrivate) {
        log.warn("Circuit breaker open for playlist-service.getCreatedPlaylists(userId={}); returning empty", userId);
        return new ApiResponseDTO<>(false, "playlist-service unavailable", List.of());
    }

    @Override
    public ApiResponseDTO<List<PlaylistPreviewDTO>> getLikedPlaylistsResponse(String userId) {
        log.warn("Circuit breaker open for playlist-service.getLikedPlaylists(userId={}); returning empty", userId);
        return new ApiResponseDTO<>(false, "playlist-service unavailable", List.of());
    }
}
