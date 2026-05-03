package com.project.cadence.controller;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.PaginatedResponseDTO;
import com.project.cadence.dto.artist.*;
import com.project.cadence.dto.user.UserPreviewDTO;
import com.project.cadence.service.ArtistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/artist")
public class ArtistController {
    private final ArtistService artistService;

    @PostMapping(path = "/upsert")
    public ResponseEntity<ApiResponseDTO<String>> upsertArtist(@Validated @RequestBody UpsertArtistDTO upsertArtistDTO) {
        return artistService.upsertArtist(upsertArtistDTO);
    }

    @DeleteMapping(path = "/delete/{artistId}")
    public ResponseEntity<ApiResponseDTO<Void>> deleteExistingArtist(@PathVariable("artistId") String artistId) {
        return artistService.deleteExistingArtist(artistId);
    }

    @GetMapping(path = "/all")
    public ResponseEntity<ApiResponseDTO<PaginatedResponseDTO<ArtistPreviewDTO>>> getAllArtists(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "24") int size, @RequestParam(required = false) String key) {
        return artistService.getAllArtists(page, size, key);
    }

    @GetMapping(path = "/{artistId}")
    public ResponseEntity<ApiResponseDTO<ArtistProfileDTO>> getArtistProfile(@PathVariable("artistId") String artistId) {
        return artistService.getArtistProfile(artistId);
    }

    @PostMapping(path = "/{artistId}/follow")
    public ResponseEntity<ApiResponseDTO<Void>> followArtist(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("artistId") String artistId
    ) {
        return artistService.followArtist(artistId, userId);
    }

    @PostMapping(path = "/{artistId}/unfollow")
    public ResponseEntity<ApiResponseDTO<Void>> unfollowArtist(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("artistId") String artistId
    ) {
        return artistService.unfollowArtist(artistId, userId);
    }

    @GetMapping(path = "/{artistId}/isFollowing")
    public ResponseEntity<ApiResponseDTO<Boolean>> isFollowing(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("artistId") String artistId
    ) {
        return artistService.isFollowing(artistId, userId);
    }

    @GetMapping(path = "/{artistId}/followers")
    public ResponseEntity<ApiResponseDTO<List<UserPreviewDTO>>> getArtistFollowers(@PathVariable("artistId") String artistId) {
        return artistService.getArtistFollowers(artistId);
    }
}
