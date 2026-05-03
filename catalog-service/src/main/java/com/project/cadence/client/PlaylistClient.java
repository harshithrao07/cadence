package com.project.cadence.client;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.playlist.PlaylistPreviewDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "playlist-service", fallback = PlaylistClientFallback.class)
public interface PlaylistClient {

    default List<PlaylistPreviewDTO> searchPlaylists(int page, int size, String key) {
        ApiResponseDTO<List<PlaylistPreviewDTO>> response = searchPlaylistsResponse(key, page, size);
        return response != null && response.success() && response.data() != null
                ? response.data()
                : List.of();
    }

    @GetMapping("/api/v1/playlist/search")
    ApiResponseDTO<List<PlaylistPreviewDTO>> searchPlaylistsResponse(
            @RequestParam(value = "key", required = false) String key,
            @RequestParam("page") int page,
            @RequestParam("size") int size
    );
}
