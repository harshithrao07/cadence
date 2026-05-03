package com.cadence.playlist_service.service;

import com.cadence.playlist_service.client.CatalogPreviewClient;
import com.cadence.playlist_service.client.UserPreviewClient;
import com.cadence.playlist_service.dto.ApiResponseDTO;
import com.cadence.playlist_service.dto.EachSongDTO;
import com.cadence.playlist_service.dto.PlaylistPreviewDTO;
import com.cadence.playlist_service.dto.SongPreviewRequestDTO;
import com.cadence.playlist_service.dto.UpsertPlaylistDTO;
import com.cadence.playlist_service.dto.UserPreviewDTO;
import com.cadence.playlist_service.model.LikedPlaylist;
import com.cadence.playlist_service.model.LikedPlaylistId;
import com.cadence.playlist_service.model.Playlist;
import com.cadence.playlist_service.model.PlaylistVisibility;
import com.cadence.playlist_service.model.SystemPlaylistType;
import com.cadence.playlist_service.repository.LikedPlaylistRepository;
import com.cadence.playlist_service.repository.PlaylistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    @Mock private PlaylistRepository playlistRepository;
    @Mock private LikedPlaylistRepository likedPlaylistRepository;
    @Mock private UserPreviewClient userPreviewClient;
    @Mock private CatalogPreviewClient catalogPreviewClient;

    @InjectMocks
    private PlaylistService playlistService;

    private static final String USER_ID = "user-1";
    private static final String OTHER_USER_ID = "user-2";
    private static final String PLAYLIST_ID = "playlist-1";
    private static final String SONG_ID = "song-1";

    private UserPreviewDTO userPreview;

    @BeforeEach
    void setUp() {
        userPreview = new UserPreviewDTO(USER_ID, "Test User", null);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Playlist publicPlaylist(String id, String ownerId) {
        return Playlist.builder()
                .id(id).name("My Playlist").ownerId(ownerId)
                .visibility(PlaylistVisibility.PUBLIC)
                .songIds(new ArrayList<>())
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    private Playlist privatePlaylist(String id, String ownerId) {
        return Playlist.builder()
                .id(id).name("Private Playlist").ownerId(ownerId)
                .visibility(PlaylistVisibility.PRIVATE)
                .songIds(new ArrayList<>())
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    private Playlist systemPlaylist(String ownerId) {
        return Playlist.builder()
                .id(SystemPlaylistType.LIKED_SONGS.name() + "_" + ownerId)
                .name("Liked Songs").ownerId(ownerId)
                .visibility(PlaylistVisibility.PRIVATE)
                .isSystem(true).systemType(SystemPlaylistType.LIKED_SONGS)
                .songIds(new ArrayList<>())
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    // ── getAllPlaylists ───────────────────────────────────────────────────────

    @Test
    void getAllPlaylists_returnsNonSystemPlaylists() {
        Playlist p = publicPlaylist(PLAYLIST_ID, USER_ID);
        when(playlistRepository.findAllByOwnerIdAndIsSystemFalseOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(p));
        when(userPreviewClient.getUserPreview(USER_ID)).thenReturn(userPreview);

        ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> response = playlistService.getAllPlaylists(USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).hasSize(1);
        assertThat(response.getBody().data().get(0).id()).isEqualTo(PLAYLIST_ID);
    }

    @Test
    void getAllPlaylists_returnsEmptyList_whenUserHasNoPlaylists() {
        when(playlistRepository.findAllByOwnerIdAndIsSystemFalseOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());

        ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> response = playlistService.getAllPlaylists(USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isEmpty();
    }

    // ── getCreatedPlaylistsForProfile ─────────────────────────────────────────

    @Test
    void getCreatedPlaylistsForProfile_includePrivateTrue_returnsAll() {
        Playlist pub = publicPlaylist(PLAYLIST_ID, USER_ID);
        Playlist priv = privatePlaylist("playlist-2", USER_ID);
        when(playlistRepository.findAllByOwnerIdAndIsSystemFalseOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(pub, priv));
        when(userPreviewClient.getUserPreview(USER_ID)).thenReturn(userPreview);

        ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> response =
                playlistService.getCreatedPlaylistsForProfile(USER_ID, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).hasSize(2);
    }

    @Test
    void getCreatedPlaylistsForProfile_includePrivateFalse_filtersPrivatePlaylists() {
        Playlist pub = publicPlaylist(PLAYLIST_ID, USER_ID);
        Playlist priv = privatePlaylist("playlist-2", USER_ID);
        when(playlistRepository.findAllByOwnerIdAndIsSystemFalseOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(pub, priv));
        when(userPreviewClient.getUserPreview(USER_ID)).thenReturn(userPreview);

        ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> response =
                playlistService.getCreatedPlaylistsForProfile(USER_ID, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).hasSize(1);
        assertThat(response.getBody().data().get(0).id()).isEqualTo(PLAYLIST_ID);
    }

    @Test
    void getCreatedPlaylistsForProfile_returnsEmptyList_whenNoPlaylists() {
        when(playlistRepository.findAllByOwnerIdAndIsSystemFalseOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());

        ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> response =
                playlistService.getCreatedPlaylistsForProfile(USER_ID, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isEmpty();
    }

    // ── getLikedPlaylistsForProfile ───────────────────────────────────────────

    @Test
    void getLikedPlaylistsForProfile_returnsPublicLikedPlaylists() {
        LikedPlaylistId id = new LikedPlaylistId(USER_ID, PLAYLIST_ID);
        LikedPlaylist liked = LikedPlaylist.builder().id(id).likeOrder(0).build();
        Playlist pub = publicPlaylist(PLAYLIST_ID, OTHER_USER_ID);
        when(likedPlaylistRepository.findByIdUserIdOrderByLikeOrderAsc(USER_ID)).thenReturn(List.of(liked));
        when(playlistRepository.findAllById(List.of(PLAYLIST_ID))).thenReturn(List.of(pub));
        when(userPreviewClient.getUserPreview(OTHER_USER_ID)).thenReturn(new UserPreviewDTO(OTHER_USER_ID, "Other", null));

        ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> response =
                playlistService.getLikedPlaylistsForProfile(USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).hasSize(1);
    }

    @Test
    void getLikedPlaylistsForProfile_filtersOutPrivatePlaylists() {
        LikedPlaylistId id = new LikedPlaylistId(USER_ID, PLAYLIST_ID);
        LikedPlaylist liked = LikedPlaylist.builder().id(id).likeOrder(0).build();
        Playlist priv = privatePlaylist(PLAYLIST_ID, OTHER_USER_ID);
        when(likedPlaylistRepository.findByIdUserIdOrderByLikeOrderAsc(USER_ID)).thenReturn(List.of(liked));
        when(playlistRepository.findAllById(List.of(PLAYLIST_ID))).thenReturn(List.of(priv));

        ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> response =
                playlistService.getLikedPlaylistsForProfile(USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isEmpty();
    }

    // ── upsertPlaylist ────────────────────────────────────────────────────────

    @Test
    void upsertPlaylist_createsNewPlaylist_whenNoIdProvided() {
        UpsertPlaylistDTO dto = new UpsertPlaylistDTO(Optional.empty(), "New Playlist", Optional.empty());
        Playlist saved = publicPlaylist(PLAYLIST_ID, USER_ID);
        when(playlistRepository.save(any(Playlist.class))).thenReturn(saved);

        ResponseEntity<ApiResponseDTO<String>> response = playlistService.upsertPlaylist(USER_ID, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
        verify(playlistRepository).save(argThat(p ->
                p.getOwnerId().equals(USER_ID) &&
                p.getName().equals("New Playlist") &&
                p.getVisibility() == PlaylistVisibility.PUBLIC
        ));
    }

    @Test
    void upsertPlaylist_updatesExistingPlaylist_whenOwnerProvidesId() {
        Playlist existing = publicPlaylist(PLAYLIST_ID, USER_ID);
        UpsertPlaylistDTO dto = new UpsertPlaylistDTO(
                Optional.of(PLAYLIST_ID), "Updated Name", Optional.of(PlaylistVisibility.PRIVATE));
        when(playlistRepository.findByIdAndOwnerId(PLAYLIST_ID, USER_ID)).thenReturn(Optional.of(existing));
        when(playlistRepository.save(any(Playlist.class))).thenReturn(existing);

        ResponseEntity<ApiResponseDTO<String>> response = playlistService.upsertPlaylist(USER_ID, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(existing.getName()).isEqualTo("Updated Name");
        assertThat(existing.getVisibility()).isEqualTo(PlaylistVisibility.PRIVATE);
    }

    @Test
    void upsertPlaylist_fails_whenPlaylistNotFound() {
        UpsertPlaylistDTO dto = new UpsertPlaylistDTO(Optional.of("nonexistent"), "Name", Optional.empty());
        when(playlistRepository.findByIdAndOwnerId("nonexistent", USER_ID)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponseDTO<String>> response = playlistService.upsertPlaylist(USER_ID, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().success()).isFalse();
    }

    @Test
    void upsertPlaylist_fails_whenEditingSystemPlaylist() {
        Playlist sys = systemPlaylist(USER_ID);
        UpsertPlaylistDTO dto = new UpsertPlaylistDTO(Optional.of(sys.getId()), "Hacked Name", Optional.empty());
        when(playlistRepository.findByIdAndOwnerId(sys.getId(), USER_ID)).thenReturn(Optional.of(sys));

        ResponseEntity<ApiResponseDTO<String>> response = playlistService.upsertPlaylist(USER_ID, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().success()).isFalse();
        verify(playlistRepository, never()).save(any());
    }

    // ── addSongToPlaylist ─────────────────────────────────────────────────────

    @Test
    void addSongToPlaylist_addsSong_whenOwnerAndSongExists() {
        Playlist p = publicPlaylist(PLAYLIST_ID, USER_ID);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));
        when(catalogPreviewClient.songExists(SONG_ID)).thenReturn(true);

        ResponseEntity<ApiResponseDTO<Void>> response = playlistService.addSongToPlaylist(USER_ID, PLAYLIST_ID, SONG_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(p.getSongIds()).contains(SONG_ID);
        verify(playlistRepository).save(p);
    }

    @Test
    void addSongToPlaylist_fails_whenNotOwner() {
        Playlist p = publicPlaylist(PLAYLIST_ID, OTHER_USER_ID);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));

        ResponseEntity<ApiResponseDTO<Void>> response = playlistService.addSongToPlaylist(USER_ID, PLAYLIST_ID, SONG_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(playlistRepository, never()).save(any());
    }

    @Test
    void addSongToPlaylist_fails_whenSongDoesNotExistInCatalog() {
        Playlist p = publicPlaylist(PLAYLIST_ID, USER_ID);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));
        when(catalogPreviewClient.songExists(SONG_ID)).thenReturn(false);

        ResponseEntity<ApiResponseDTO<Void>> response = playlistService.addSongToPlaylist(USER_ID, PLAYLIST_ID, SONG_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(playlistRepository, never()).save(any());
    }

    @Test
    void addSongToPlaylist_doesNotAddDuplicate_whenSongAlreadyInPlaylist() {
        Playlist p = publicPlaylist(PLAYLIST_ID, USER_ID);
        p.getSongIds().add(SONG_ID);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));
        when(catalogPreviewClient.songExists(SONG_ID)).thenReturn(true);

        playlistService.addSongToPlaylist(USER_ID, PLAYLIST_ID, SONG_ID);

        assertThat(p.getSongIds()).hasSize(1);
        verify(playlistRepository, never()).save(any());
    }

    // ── removeSongFromPlaylist ────────────────────────────────────────────────

    @Test
    void removeSongFromPlaylist_removesSong_whenOwnerAndSongPresent() {
        Playlist p = publicPlaylist(PLAYLIST_ID, USER_ID);
        p.getSongIds().add(SONG_ID);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));

        ResponseEntity<ApiResponseDTO<Void>> response = playlistService.removeSongFromPlaylist(USER_ID, PLAYLIST_ID, SONG_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(p.getSongIds()).doesNotContain(SONG_ID);
        verify(playlistRepository).save(p);
    }

    @Test
    void removeSongFromPlaylist_fails_whenNotOwner() {
        Playlist p = publicPlaylist(PLAYLIST_ID, OTHER_USER_ID);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));

        ResponseEntity<ApiResponseDTO<Void>> response = playlistService.removeSongFromPlaylist(USER_ID, PLAYLIST_ID, SONG_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(playlistRepository, never()).save(any());
    }

    @Test
    void removeSongFromPlaylist_returns400_whenSongNotInPlaylist() {
        Playlist p = publicPlaylist(PLAYLIST_ID, USER_ID);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));

        ResponseEntity<ApiResponseDTO<Void>> response = playlistService.removeSongFromPlaylist(USER_ID, PLAYLIST_ID, SONG_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Song not present in playlist");
    }

    // ── likePlaylist ──────────────────────────────────────────────────────────

    @Test
    void likePlaylist_savesLike_withCorrectLikeOrder() {
        when(playlistRepository.existsById(PLAYLIST_ID)).thenReturn(true);
        when(likedPlaylistRepository.existsById(any())).thenReturn(false);
        when(likedPlaylistRepository.countByIdUserId(USER_ID)).thenReturn(3L);

        ResponseEntity<ApiResponseDTO<Void>> response = playlistService.likePlaylist(USER_ID, PLAYLIST_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(likedPlaylistRepository).save(argThat(l -> l.getLikeOrder() == 3));
    }

    @Test
    void likePlaylist_fails_whenPlaylistDoesNotExist() {
        when(playlistRepository.existsById(PLAYLIST_ID)).thenReturn(false);

        ResponseEntity<ApiResponseDTO<Void>> response = playlistService.likePlaylist(USER_ID, PLAYLIST_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(likedPlaylistRepository, never()).save(any());
    }

    @Test
    void likePlaylist_doesNotSaveDuplicate_whenAlreadyLiked() {
        when(playlistRepository.existsById(PLAYLIST_ID)).thenReturn(true);
        when(likedPlaylistRepository.existsById(any())).thenReturn(true);

        ResponseEntity<ApiResponseDTO<Void>> response = playlistService.likePlaylist(USER_ID, PLAYLIST_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(likedPlaylistRepository, never()).save(any());
    }

    // ── unlikePlaylist ────────────────────────────────────────────────────────

    @Test
    void unlikePlaylist_deletesLike_whenExists() {
        when(likedPlaylistRepository.existsById(any())).thenReturn(true);

        ResponseEntity<ApiResponseDTO<Void>> response = playlistService.unlikePlaylist(USER_ID, PLAYLIST_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(likedPlaylistRepository).deleteById(new LikedPlaylistId(USER_ID, PLAYLIST_ID));
    }

    @Test
    void unlikePlaylist_returns200_andSkipsDelete_whenNotLiked() {
        when(likedPlaylistRepository.existsById(any())).thenReturn(false);

        ResponseEntity<ApiResponseDTO<Void>> response = playlistService.unlikePlaylist(USER_ID, PLAYLIST_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(likedPlaylistRepository, never()).deleteById(any());
    }

    // ── createLikedSongsPlaylistForUser ───────────────────────────────────────

    @Test
    void createLikedSongsPlaylistForUser_createsSystemPlaylist() {
        String expectedId = SystemPlaylistType.LIKED_SONGS.name() + "_" + USER_ID;
        when(playlistRepository.existsById(expectedId)).thenReturn(false);

        playlistService.createLikedSongsPlaylistForUser(USER_ID);

        verify(playlistRepository).save(argThat(p ->
                p.isSystem() &&
                p.getSystemType() == SystemPlaylistType.LIKED_SONGS &&
                p.getOwnerId().equals(USER_ID) &&
                p.getVisibility() == PlaylistVisibility.PRIVATE
        ));
    }

    @Test
    void createLikedSongsPlaylistForUser_isIdempotent_whenPlaylistAlreadyExists() {
        String expectedId = SystemPlaylistType.LIKED_SONGS.name() + "_" + USER_ID;
        when(playlistRepository.existsById(expectedId)).thenReturn(true);

        playlistService.createLikedSongsPlaylistForUser(USER_ID);

        verify(playlistRepository, never()).save(any());
    }

    @Test
    void createLikedSongsPlaylistForUser_doesNothing_whenUserIdIsNull() {
        playlistService.createLikedSongsPlaylistForUser(null);

        verify(playlistRepository, never()).existsById(any());
        verify(playlistRepository, never()).save(any());
    }

    @Test
    void createLikedSongsPlaylistForUser_doesNothing_whenUserIdIsBlank() {
        playlistService.createLikedSongsPlaylistForUser("   ");

        verify(playlistRepository, never()).existsById(any());
        verify(playlistRepository, never()).save(any());
    }

    // ── getPlaylist ───────────────────────────────────────────────────────────

    @Test
    void getPlaylist_returns200_whenOwnerRequestsPrivatePlaylist() {
        Playlist p = privatePlaylist(PLAYLIST_ID, USER_ID);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));
        when(userPreviewClient.getUserPreview(USER_ID)).thenReturn(userPreview);

        ResponseEntity<ApiResponseDTO<PlaylistPreviewDTO>> response = playlistService.getPlaylist(USER_ID, PLAYLIST_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getPlaylist_returns403_whenNonOwnerRequestsPrivatePlaylist() {
        Playlist p = privatePlaylist(PLAYLIST_ID, OTHER_USER_ID);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));

        ResponseEntity<ApiResponseDTO<PlaylistPreviewDTO>> response = playlistService.getPlaylist(USER_ID, PLAYLIST_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo("Access denied");
    }

    @Test
    void getPlaylist_returns200_forPublicPlaylist_fromAnyUser() {
        Playlist p = publicPlaylist(PLAYLIST_ID, OTHER_USER_ID);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));
        when(userPreviewClient.getUserPreview(OTHER_USER_ID)).thenReturn(new UserPreviewDTO(OTHER_USER_ID, "Other", null));

        ResponseEntity<ApiResponseDTO<PlaylistPreviewDTO>> response = playlistService.getPlaylist(USER_ID, PLAYLIST_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getPlaylist_returns500_whenPlaylistNotFound() {
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponseDTO<PlaylistPreviewDTO>> response = playlistService.getPlaylist(USER_ID, PLAYLIST_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── getSongsFromPlaylist ──────────────────────────────────────────────────

    @Test
    void getSongsFromPlaylist_returns200_whenOwnerRequestsPrivatePlaylist() {
        Playlist p = privatePlaylist(PLAYLIST_ID, USER_ID);
        // empty song list — default method short-circuits before calling Feign
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));

        ResponseEntity<ApiResponseDTO<List<EachSongDTO>>> response =
                playlistService.getSongsFromPlaylist(USER_ID, PLAYLIST_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getSongsFromPlaylist_returns403_whenNonOwnerRequestsPrivatePlaylist() {
        Playlist p = privatePlaylist(PLAYLIST_ID, OTHER_USER_ID);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));

        ResponseEntity<ApiResponseDTO<List<EachSongDTO>>> response =
                playlistService.getSongsFromPlaylist(USER_ID, PLAYLIST_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSongsFromPlaylist_reversesSongOrder_forLikedSongsSystemPlaylist() {
        String song1 = "song-1", song2 = "song-2", song3 = "song-3";
        Playlist p = systemPlaylist(USER_ID);
        p.getSongIds().addAll(List.of(song1, song2, song3));
        when(playlistRepository.findById(p.getId())).thenReturn(Optional.of(p));
        // cast to List<String> to select the default-method overload (Mockito mocks it directly)
        doReturn(List.of()).when(catalogPreviewClient).getSongPreviews((List<String>) any());

        playlistService.getSongsFromPlaylist(USER_ID, p.getId());

        verify(catalogPreviewClient).getSongPreviews(
                argThat((List<String> list) -> list.equals(List.of(song3, song2, song1)))
        );
    }

    @Test
    void getSongsFromPlaylist_returns500_whenPlaylistNotFound() {
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponseDTO<List<EachSongDTO>>> response =
                playlistService.getSongsFromPlaylist(USER_ID, PLAYLIST_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── deletePlaylist ────────────────────────────────────────────────────────

    @Test
    void deletePlaylist_deletesPlaylist_whenOwner() {
        Playlist p = publicPlaylist(PLAYLIST_ID, USER_ID);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));

        ResponseEntity<ApiResponseDTO<Void>> response = playlistService.deletePlaylist(USER_ID, PLAYLIST_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(playlistRepository).delete(p);
    }

    @Test
    void deletePlaylist_returns403_whenNotOwner() {
        Playlist p = publicPlaylist(PLAYLIST_ID, OTHER_USER_ID);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));

        ResponseEntity<ApiResponseDTO<Void>> response = playlistService.deletePlaylist(USER_ID, PLAYLIST_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(playlistRepository, never()).delete(any());
    }

    @Test
    void deletePlaylist_returns400_whenSystemPlaylist() {
        Playlist p = systemPlaylist(USER_ID);
        when(playlistRepository.findById(p.getId())).thenReturn(Optional.of(p));

        ResponseEntity<ApiResponseDTO<Void>> response = playlistService.deletePlaylist(USER_ID, p.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("System playlists cannot be deleted");
        verify(playlistRepository, never()).delete(any());
    }

    @Test
    void deletePlaylist_returns500_whenPlaylistNotFound() {
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponseDTO<Void>> response = playlistService.deletePlaylist(USER_ID, PLAYLIST_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── searchPlaylists ───────────────────────────────────────────────────────

    @Test
    void searchPlaylists_returnsMatchingPublicPlaylists() {
        Pageable pageable = PageRequest.of(0, 10);
        Playlist p = publicPlaylist(PLAYLIST_ID, USER_ID);
        when(playlistRepository.findByVisibilityAndNameContainingIgnoreCase(PlaylistVisibility.PUBLIC, "my", pageable))
                .thenReturn(new PageImpl<>(List.of(p)));
        when(userPreviewClient.getUserPreview(USER_ID)).thenReturn(userPreview);

        ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> response =
                playlistService.searchPlaylists(pageable, "my");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).hasSize(1);
    }

    @Test
    void searchPlaylists_treatsNullKeyAsEmptyString() {
        Pageable pageable = PageRequest.of(0, 10);
        when(playlistRepository.findByVisibilityAndNameContainingIgnoreCase(PlaylistVisibility.PUBLIC, "", pageable))
                .thenReturn(Page.empty());

        ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> response =
                playlistService.searchPlaylists(pageable, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(playlistRepository).findByVisibilityAndNameContainingIgnoreCase(PlaylistVisibility.PUBLIC, "", pageable);
    }
}
