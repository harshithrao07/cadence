package com.cadence.auth_service.service;

import com.cadence.auth_service.client.PlaylistClient;
import com.cadence.auth_service.dto.ApiResponseDTO;
import com.cadence.auth_service.dto.playlist.PlaylistPreviewDTO;
import com.cadence.auth_service.dto.user.UserProfileChangeDTO;
import com.cadence.auth_service.dto.user.UserProfileDTO;
import com.cadence.auth_service.model.Role;
import com.cadence.auth_service.model.User;
import com.cadence.auth_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PlaylistClient playlistClient;

    @InjectMocks
    private UserService userService;

    private static final String USER_ID    = "user-1";
    private static final String USER_EMAIL = "alice@example.com";
    private static final String OTHER_EMAIL = "other@example.com";

    private User user() {
        return User.builder()
                .id(USER_ID)
                .name("Alice")
                .email(USER_EMAIL)
                .role(Role.USER)
                .emailVerified(true)
                .build();
    }

    // ── getUserProfile ────────────────────────────────────────────────────────

    @Test
    void getUserProfile_returnsFullProfile_forOwner() {
        User u = user();
        List<PlaylistPreviewDTO> created = List.of();
        List<PlaylistPreviewDTO> liked = List.of();
        // authenticatedUserMatchesProfileLookup calls findById
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(u));
        when(playlistClient.getCreatedPlaylists(USER_ID, true)).thenReturn(created);
        when(playlistClient.getLikedPlaylists(USER_ID)).thenReturn(liked);

        ResponseEntity<ApiResponseDTO<UserProfileDTO>> response =
                userService.getUserProfile(USER_ID, USER_EMAIL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserProfileDTO dto = response.getBody().data();
        assertThat(dto.email()).isEqualTo(USER_EMAIL);
        assertThat(dto.isOwner()).isTrue();
        assertThat(dto.likedPlaylistsPreview()).isEqualTo(liked);
    }

    @Test
    void getUserProfile_hidesEmail_andSkipsLikedPlaylists_forVisitor() {
        User u = user();
        // findById called twice: once for ownership check, once for profile
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(u));
        when(playlistClient.getCreatedPlaylists(USER_ID, false)).thenReturn(List.of());

        ResponseEntity<ApiResponseDTO<UserProfileDTO>> response =
                userService.getUserProfile(USER_ID, OTHER_EMAIL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserProfileDTO dto = response.getBody().data();
        assertThat(dto.email()).isNull();
        assertThat(dto.isOwner()).isFalse();
        assertThat(dto.likedPlaylistsPreview()).isEmpty();
        verify(playlistClient, never()).getLikedPlaylists(any());
    }

    @Test
    void getUserProfile_returns404_whenUserNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponseDTO<UserProfileDTO>> response =
                userService.getUserProfile(USER_ID, USER_EMAIL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("User not found");
    }

    // ── putUserProfile ────────────────────────────────────────────────────────

    @Test
    void putUserProfile_updatesName_forOwner() {
        User u = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(u));
        UserProfileChangeDTO dto = new UserProfileChangeDTO(Optional.of("NewName"));

        ResponseEntity<ApiResponseDTO<Void>> response =
                userService.putUserProfile(USER_ID, dto, USER_EMAIL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(u.getName()).isEqualTo("NewName");
        verify(userRepository).save(u);
    }

    @Test
    void putUserProfile_returns401_forNonOwner() {
        User u = user();
        // ownership check: findById returns user with different email
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(u));
        UserProfileChangeDTO dto = new UserProfileChangeDTO(Optional.of("Hacker"));

        ResponseEntity<ApiResponseDTO<Void>> response =
                userService.putUserProfile(USER_ID, dto, OTHER_EMAIL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(userRepository, never()).save(any());
    }

    @Test
    void putUserProfile_doesNotChangeName_whenNameIsEmpty() {
        User u = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(u));
        UserProfileChangeDTO dto = new UserProfileChangeDTO(Optional.of(""));

        userService.putUserProfile(USER_ID, dto, USER_EMAIL);

        assertThat(u.getName()).isEqualTo("Alice");
        verify(userRepository).save(u);
    }

    // ── loadUserByUsername ────────────────────────────────────────────────────

    @Test
    void loadUserByUsername_returnsUser_whenFound() {
        User u = user();
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(u));

        assertThat(userService.loadUserByUsername(USER_EMAIL)).isEqualTo(u);
    }

    @Test
    void loadUserByUsername_throwsException_whenNotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserByUsername("unknown@example.com"))
                .isInstanceOf(RuntimeException.class);
    }
}
