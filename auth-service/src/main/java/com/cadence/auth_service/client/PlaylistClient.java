package com.cadence.auth_service.client;

import com.cadence.auth_service.dto.ApiResponseDTO;
import com.cadence.auth_service.dto.playlist.PlaylistPreviewDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "playlist-service", fallback = PlaylistClientFallback.class)
public interface PlaylistClient {

    default List<PlaylistPreviewDTO> searchPlaylists(int page, int size, String key) {
        return unwrap(searchPlaylistsResponse(key, page, size));
    }

    default List<PlaylistPreviewDTO> getCreatedPlaylists(String userId, boolean includePrivate) {
        return unwrap(getCreatedPlaylistsResponse(userId, includePrivate));
    }

    default List<PlaylistPreviewDTO> getLikedPlaylists(String userId) {
        return unwrap(getLikedPlaylistsResponse(userId));
    }

    @GetMapping("/api/v1/playlist/search")
    ApiResponseDTO<List<PlaylistPreviewDTO>> searchPlaylistsResponse(
            @RequestParam(value = "key", required = false) String key,
            @RequestParam("page") int page,
            @RequestParam("size") int size
    );

    @GetMapping("/api/v1/playlist/users/{userId}/created")
    ApiResponseDTO<List<PlaylistPreviewDTO>> getCreatedPlaylistsResponse(
            @PathVariable("userId") String userId,
            @RequestParam("includePrivate") boolean includePrivate
    );

    @GetMapping("/api/v1/playlist/users/{userId}/liked")
    ApiResponseDTO<List<PlaylistPreviewDTO>> getLikedPlaylistsResponse(@PathVariable("userId") String userId);

    private List<PlaylistPreviewDTO> unwrap(ApiResponseDTO<List<PlaylistPreviewDTO>> response) {
        return response != null && response.success() && response.data() != null
                ? response.data()
                : List.of();
    }
}
