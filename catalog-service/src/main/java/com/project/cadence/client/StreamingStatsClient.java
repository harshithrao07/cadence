package com.project.cadence.client;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.internal.PlayHistoryDTO;
import com.project.cadence.dto.internal.SongPlayCountDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.List;

@FeignClient(name = "streaming-service")
public interface StreamingStatsClient {

    default List<SongPlayCountDTO> getTrendingSongs(Instant since, int page, int size) {
        return unwrapSongPlayCounts(getTrendingSongsResponse(since, page, size));
    }

    default List<PlayHistoryDTO> getRecentHistory(String userId, int page, int size) {
        return unwrapPlayHistory(getRecentHistoryResponse(userId, page, size));
    }

    default List<PlayHistoryDTO> getUserTopSongs(String userId, int page, int size) {
        return unwrapPlayHistory(getUserTopSongsResponse(userId, page, size));
    }

    @GetMapping("/api/v1/stream/stats/trending")
    ApiResponseDTO<List<SongPlayCountDTO>> getTrendingSongsResponse(
            @RequestParam(value = "since", required = false) Instant since,
            @RequestParam("page") int page,
            @RequestParam("size") int size
    );

    @GetMapping("/api/v1/stream/history")
    ApiResponseDTO<List<PlayHistoryDTO>> getRecentHistoryResponse(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("page") int page,
            @RequestParam("size") int size
    );

    @GetMapping("/api/v1/stream/stats/users/me/top-songs")
    ApiResponseDTO<List<PlayHistoryDTO>> getUserTopSongsResponse(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("page") int page,
            @RequestParam("size") int size
    );

    private List<SongPlayCountDTO> unwrapSongPlayCounts(ApiResponseDTO<List<SongPlayCountDTO>> response) {
        return response != null && response.success() && response.data() != null
                ? response.data()
                : List.of();
    }

    private List<PlayHistoryDTO> unwrapPlayHistory(ApiResponseDTO<List<PlayHistoryDTO>> response) {
        return response != null && response.success() && response.data() != null
                ? response.data()
                : List.of();
    }
}
