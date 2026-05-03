package com.cadence.streaming_service.controller;

import com.cadence.streaming_service.dto.ApiResponseDTO;
import com.cadence.streaming_service.dto.PlayHistoryDTO;
import com.cadence.streaming_service.dto.SongPlayCountDTO;
import com.cadence.streaming_service.dto.SongPlayStatsDTO;
import com.cadence.streaming_service.service.PlayHistoryService;
import com.cadence.streaming_service.service.StreamingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/stream")
public class StreamingController {
    private final StreamingService streamingService;
    private final PlayHistoryService playHistoryService;

    @GetMapping("/song/{songId}")
    public ResponseEntity<StreamingResponseBody> streamSongById(
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest request,
            @PathVariable String songId
    ) {
        return streamingService.streamSongById(songId, userId, request.getHeader("Range"));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponseDTO<List<PlayHistoryDTO>>> getRecentHistory(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true,
                "Play history fetched successfully",
                playHistoryService.getRecentHistory(userId, page, size)
        ));
    }

    @GetMapping("/stats/users/me/top-songs")
    public ResponseEntity<ApiResponseDTO<List<PlayHistoryDTO>>> getMyTopSongs(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true,
                "Top songs fetched successfully",
                playHistoryService.getUserTopSongs(userId, page, size)
        ));
    }

    @GetMapping("/stats/trending")
    public ResponseEntity<ApiResponseDTO<List<SongPlayCountDTO>>> getTrendingSongs(
            @RequestParam(required = false) Instant since,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true,
                "Trending songs fetched successfully",
                playHistoryService.getTrendingSongs(since, page, size)
        ));
    }

    @GetMapping("/stats/songs/{songId}")
    public ResponseEntity<ApiResponseDTO<SongPlayStatsDTO>> getSongStats(@PathVariable String songId) {
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true,
                "Song stats fetched successfully",
                playHistoryService.getSongStats(songId)
        ));
    }

    @GetMapping("/stats/songs/play-counts")
    public ResponseEntity<ApiResponseDTO<List<SongPlayCountDTO>>> getSongPlayCounts(
            @RequestParam List<String> songIds
    ) {
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true,
                "Song play counts fetched successfully",
                playHistoryService.getSongPlayCounts(songIds)
        ));
    }

    @GetMapping("/stats/listeners")
    public ResponseEntity<ApiResponseDTO<Long>> getUniqueListeners(
            @RequestParam List<String> songIds,
            @RequestParam(required = false) Instant since
    ) {
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true,
                "Unique listeners fetched successfully",
                playHistoryService.getUniqueListeners(songIds, since)
        ));
    }
}
