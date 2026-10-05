package com.project.cadence.controller;

import com.project.cadence.dto.artist.ArtistPreviewDTO;
import com.project.cadence.service.ArtistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service-to-service reads about users that catalog owns data for (artist follows).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/users")
public class InternalUserController {
    private final ArtistService artistService;

    /** Artists the user follows, in follow order. Used by auth-service's profile page. */
    @GetMapping("/{userId}/followed-artists")
    public ResponseEntity<List<ArtistPreviewDTO>> getFollowedArtists(@PathVariable String userId) {
        return ResponseEntity.ok(artistService.getFollowedArtists(userId));
    }
}
