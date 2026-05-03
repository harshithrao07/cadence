package com.cadence.auth_service.service;

import com.cadence.auth_service.client.PlaylistClient;
import com.cadence.auth_service.dto.ApiResponseDTO;
import com.cadence.auth_service.dto.artist.ArtistPreviewDTO;
import com.cadence.auth_service.dto.playlist.PlaylistPreviewDTO;
import com.cadence.auth_service.dto.user.UserProfileChangeDTO;
import com.cadence.auth_service.dto.user.UserProfileDTO;
import com.cadence.auth_service.dto.user.UserPreviewDTO;
import com.cadence.auth_service.model.*;
import com.cadence.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    private final UserRepository userRepository;
    private final PlaylistClient playlistClient;

    private boolean authenticatedUserMatchesProfileLookup(String tokenEmail, String userId) {
        Optional<User> user = userRepository.findById(userId);
        return user.isPresent() && user.get().getEmail().equals(tokenEmail);
    }

    public ResponseEntity<ApiResponseDTO<UserProfileDTO>> getUserProfile(
            String userId,
            String tokenEmail
    ) {
        try {
            // same endpoint for both owner and visitor

            boolean matches = authenticatedUserMatchesProfileLookup(tokenEmail, userId);
            User user = userRepository.findById(userId)
                    .orElse(null);

            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponseDTO<>(false, "User not found", null));
            }

            List<PlaylistPreviewDTO> createdPlaylistsPreview =
                    playlistClient.getCreatedPlaylists(userId, matches);

            List<PlaylistPreviewDTO> likedPlaylistsPreview = matches
                    ? playlistClient.getLikedPlaylists(userId)
                    : List.of();

            List<ArtistPreviewDTO> artistFollowingPreview = new ArrayList<>();
            user.getArtistFollowing().forEach(artist ->
                    artistFollowingPreview.add(
                            new ArtistPreviewDTO(
                                    artist.getId(),
                                    artist.getName(),
                                    artist.getProfileUrl()
                            )
                    )
            );

            UserProfileDTO userProfileDTO = new UserProfileDTO(
                    user.getId(),
                    user.getName(),
                    matches ? user.getEmail() : null,
                    user.getProfileUrl(),
                    createdPlaylistsPreview,
                    likedPlaylistsPreview,
                    artistFollowingPreview,
                    user.isEmailVerified(),
                    matches
            );

            return ResponseEntity.ok(
                    new ApiResponseDTO<>(
                            true,
                            "Successfully retrieved user profile",
                            userProfileDTO
                    )
            );

        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>(
                            false,
                            "An error occurred in the server",
                            null
                    ));
        }
    }


    public ResponseEntity<ApiResponseDTO<Void>> putUserProfile(String userId, UserProfileChangeDTO userProfileChangeDTO, String tokenEmail) {
        try {
            // Whether the token email matches with the requested profile's email if not then unauthorized
            if (!authenticatedUserMatchesProfileLookup(tokenEmail, userId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponseDTO<>(false, "Not authorized to edit profile", null));
            }

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponseDTO<>(false, "An error occurred: requesting user not found", null));
            }

            if (userProfileChangeDTO.name().isPresent() && !userProfileChangeDTO.name().get().isEmpty()) {
                user.setName(userProfileChangeDTO.name().get());
            }

            userRepository.save(user);
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseDTO<>(true, "User profile updated successfully", null));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
