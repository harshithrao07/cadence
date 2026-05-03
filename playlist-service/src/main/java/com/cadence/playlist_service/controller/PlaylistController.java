package com.cadence.playlist_service.controller;

import com.cadence.playlist_service.dto.ApiResponseDTO;
import com.cadence.playlist_service.dto.EachSongDTO;
import com.cadence.playlist_service.dto.PlaylistPreviewDTO;
import com.cadence.playlist_service.dto.UpsertPlaylistDTO;
import com.cadence.playlist_service.service.PlaylistService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/playlist")
public class PlaylistController {

    private final PlaylistService playlistService;

    @GetMapping("/all")
    public ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> getAllPlaylists(
            @RequestHeader("X-User-Id") String userId
    ) {
        return playlistService.getAllPlaylists(userId);
    }

    @GetMapping("/users/{userId}/created")
    public ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> getCreatedPlaylistsForProfile(
            @PathVariable String userId,
            @RequestParam(defaultValue = "false") boolean includePrivate
    ) {
        return playlistService.getCreatedPlaylistsForProfile(userId, includePrivate);
    }

    @GetMapping("/users/{userId}/liked")
    public ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> getLikedPlaylistsForProfile(
            @PathVariable String userId
    ) {
        return playlistService.getLikedPlaylistsForProfile(userId);
    }

    @PostMapping("/upsert")
    public ResponseEntity<ApiResponseDTO<String>> addNewPlaylist(
            @RequestHeader("X-User-Id") String userId,
            @Validated @RequestBody UpsertPlaylistDTO upsertPlaylistDTO
    ) {
        return playlistService.upsertPlaylist(userId, upsertPlaylistDTO);
    }

    @PutMapping("/{playlistId}/song/{songId}")
    public ResponseEntity<ApiResponseDTO<Void>> addSongToPlaylist(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String playlistId,
            @PathVariable String songId
    ) {
        return playlistService.addSongToPlaylist(userId, playlistId, songId);
    }

    @DeleteMapping("/{playlistId}/song/{songId}")
    public ResponseEntity<ApiResponseDTO<Void>> removeSongFromPlaylist(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String playlistId,
            @PathVariable String songId
    ) {
        return playlistService.removeSongFromPlaylist(userId, playlistId, songId);
    }

    @PutMapping("/{playlistId}/like")
    public ResponseEntity<ApiResponseDTO<Void>> likePlaylist(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String playlistId
    ) {
        return playlistService.likePlaylist(userId, playlistId);
    }

    @DeleteMapping("/{playlistId}/like")
    public ResponseEntity<ApiResponseDTO<Void>> unlikePlaylist(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String playlistId
    ) {
        return playlistService.unlikePlaylist(userId, playlistId);
    }

    @GetMapping("/{playlistId}")
    public ResponseEntity<ApiResponseDTO<PlaylistPreviewDTO>> getPlaylist(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String playlistId
    ) {
        return playlistService.getPlaylist(userId, playlistId);
    }

    @GetMapping("/{playlistId}/songs")
    public ResponseEntity<ApiResponseDTO<List<EachSongDTO>>> getSongsFromPlaylist(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String playlistId
    ) {
        return playlistService.getSongsFromPlaylist(userId, playlistId);
    }

    @DeleteMapping("/{playlistId}")
    public ResponseEntity<ApiResponseDTO<Void>> deletePlaylist(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String playlistId
    ) {
        return playlistService.deletePlaylist(userId, playlistId);
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> searchPlaylists(
            @RequestParam(required = false) String key,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return playlistService.searchPlaylists(pageable, key);
    }
}
