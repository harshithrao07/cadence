package com.cadence.playlist_service.controller;

import com.cadence.playlist_service.model.Playlist;
import com.cadence.playlist_service.repository.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service reads. Not routed by the gateway.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/playlists")
public class InternalPlaylistController {
    private final PlaylistRepository playlistRepository;

    /** Owner's user id; 404 if the playlist doesn't exist. Used by catalog to authorize cover uploads. */
    @GetMapping("/{playlistId}/owner")
    public ResponseEntity<String> getOwner(@PathVariable String playlistId) {
        return playlistRepository.findById(playlistId)
                .map(Playlist::getOwnerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
