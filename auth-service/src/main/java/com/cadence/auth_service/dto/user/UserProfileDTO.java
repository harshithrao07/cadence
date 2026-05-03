package com.cadence.auth_service.dto.user;

import com.cadence.auth_service.dto.artist.ArtistPreviewDTO;
import com.cadence.auth_service.dto.playlist.PlaylistPreviewDTO;

import java.util.List;

public record UserProfileDTO(
        String id,
        String name,
        String email,
        String profileUrl,
        List<PlaylistPreviewDTO> createdPlaylistsPreview,
        List<PlaylistPreviewDTO> likedPlaylistsPreview,
        List<ArtistPreviewDTO> artistFollowing,
        boolean emailVerified,
        boolean isOwner
) {
}
