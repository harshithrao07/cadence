package com.project.cadence.service;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.PaginatedResponseDTO;
import com.project.cadence.dto.artist.ArtistPreviewDTO;
import com.project.cadence.model.Artist;
import com.project.cadence.repository.ArtistRepository;
import com.project.cadence.repository.RecordRepository;
import com.project.cadence.repository.SongRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArtistServiceTest {

    @Mock private ArtistRepository artistRepository;
    @Mock private SongRepository songRepository;
    @Mock private RecordRepository recordRepository;
    @Mock private AwsService awsService;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ArtistService artistService;

    private static final String ARTIST_ID = "artist-1";
    private static final String USER_ID   = "user-1";

    private Artist artist() {
        return Artist.builder().id(ARTIST_ID).name("Drake").build();
    }

    private void stubUserExists(int count) {
        when(jdbcTemplate.queryForObject(
                contains("FROM users"),
                eq(Integer.class),
                any(Object[].class)
        )).thenReturn(count);
    }

    private void stubIsFollowing(int count) {
        when(jdbcTemplate.queryForObject(
                contains("AND artist_id"),
                eq(Integer.class),
                any(Object[].class)
        )).thenReturn(count);
    }

    private void stubNextFollowOrder(int order) {
        when(jdbcTemplate.queryForObject(
                contains("COALESCE"),
                eq(Integer.class),
                any(Object[].class)
        )).thenReturn(order);
    }

    // ── followArtist ──────────────────────────────────────────────────────────

    @Test
    void followArtist_returns404_whenUserNotFound() {
        stubUserExists(0);

        ResponseEntity<ApiResponseDTO<Void>> response = artistService.followArtist(ARTIST_ID, USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).contains("requesting user not found");
    }

    @Test
    void followArtist_returns404_whenArtistNotFound() {
        stubUserExists(1);
        when(artistRepository.existsById(ARTIST_ID)).thenReturn(false);

        ResponseEntity<ApiResponseDTO<Void>> response = artistService.followArtist(ARTIST_ID, USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Artist not found");
    }

    @Test
    void followArtist_returns400_whenAlreadyFollowing() {
        stubUserExists(1);
        stubIsFollowing(1);
        when(artistRepository.existsById(ARTIST_ID)).thenReturn(true);

        ResponseEntity<ApiResponseDTO<Void>> response = artistService.followArtist(ARTIST_ID, USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("already following");
    }

    @Test
    void followArtist_returns200_onSuccess() {
        stubUserExists(1);
        stubIsFollowing(0);
        stubNextFollowOrder(0);
        when(artistRepository.existsById(ARTIST_ID)).thenReturn(true);

        ResponseEntity<ApiResponseDTO<Void>> response = artistService.followArtist(ARTIST_ID, USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
    }

    // ── unfollowArtist ────────────────────────────────────────────────────────

    @Test
    void unfollowArtist_returns404_whenUserNotFound() {
        stubUserExists(0);

        ResponseEntity<ApiResponseDTO<Void>> response = artistService.unfollowArtist(ARTIST_ID, USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unfollowArtist_returns400_whenNotFollowing() {
        stubUserExists(1);
        stubIsFollowing(0);
        when(artistRepository.existsById(ARTIST_ID)).thenReturn(true);

        ResponseEntity<ApiResponseDTO<Void>> response = artistService.unfollowArtist(ARTIST_ID, USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("not following");
    }

    @Test
    void unfollowArtist_returns200_onSuccess() {
        stubUserExists(1);
        stubIsFollowing(1);
        when(artistRepository.existsById(ARTIST_ID)).thenReturn(true);

        ResponseEntity<ApiResponseDTO<Void>> response = artistService.unfollowArtist(ARTIST_ID, USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
    }

    // ── isFollowing ───────────────────────────────────────────────────────────

    @Test
    void isFollowing_returnsTrue_whenUserFollowsArtist() {
        stubUserExists(1);
        stubIsFollowing(1);
        when(artistRepository.existsById(ARTIST_ID)).thenReturn(true);

        ResponseEntity<ApiResponseDTO<Boolean>> response = artistService.isFollowing(ARTIST_ID, USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isTrue();
    }

    @Test
    void isFollowing_returnsFalse_whenUserDoesNotFollowArtist() {
        stubUserExists(1);
        stubIsFollowing(0);
        when(artistRepository.existsById(ARTIST_ID)).thenReturn(true);

        ResponseEntity<ApiResponseDTO<Boolean>> response = artistService.isFollowing(ARTIST_ID, USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isFalse();
    }

    // ── getAllArtists ─────────────────────────────────────────────────────────

    @Test
    void getAllArtists_withoutKey_returnsPaginatedArtists() {
        Artist a = artist();
        when(artistRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a), PageRequest.of(0, 10), 1));

        ResponseEntity<ApiResponseDTO<PaginatedResponseDTO<ArtistPreviewDTO>>> response =
                artistService.getAllArtists(0, 10, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content()).hasSize(1);
        assertThat(response.getBody().data().content().get(0).name()).isEqualTo("Drake");
    }

    @Test
    void getAllArtists_withKey_filtersArtistsByPrefix() {
        Artist a = artist();
        when(artistRepository.findByNameStartingWithIgnoreCase(eq("Dr"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a), PageRequest.of(0, 10), 1));

        ResponseEntity<ApiResponseDTO<PaginatedResponseDTO<ArtistPreviewDTO>>> response =
                artistService.getAllArtists(0, 10, "Dr");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content()).hasSize(1);
    }

    // ── getArtistProfile ──────────────────────────────────────────────────────

    @Test
    void getArtistProfile_returns404_whenArtistNotFound() {
        when(artistRepository.findById(ARTIST_ID)).thenReturn(Optional.empty());

        ResponseEntity<?> response = artistService.getArtistProfile(ARTIST_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
