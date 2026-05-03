package com.cadence.playlist_service.integration;

import com.cadence.playlist_service.dto.ApiResponseDTO;
import com.cadence.playlist_service.dto.PlaylistPreviewDTO;
import com.cadence.playlist_service.dto.UpsertPlaylistDTO;
import com.cadence.playlist_service.model.LikedPlaylistId;
import com.cadence.playlist_service.model.Playlist;
import com.cadence.playlist_service.model.PlaylistVisibility;
import com.cadence.playlist_service.model.SystemPlaylistType;
import com.cadence.playlist_service.repository.LikedPlaylistRepository;
import com.cadence.playlist_service.repository.PlaylistRepository;
import com.cadence.playlist_service.service.PlaylistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class PlaylistServiceIT extends BaseIntegrationTest {

    @Autowired PlaylistService playlistService;
    @Autowired PlaylistRepository playlistRepository;
    @Autowired LikedPlaylistRepository likedPlaylistRepository;

    private static final String OWNER = "user-1";
    private static final String OTHER_USER = "user-2";

    @BeforeEach
    void setUp() {
        likedPlaylistRepository.deleteAll();
        playlistRepository.deleteAll();
    }

    @Test
    void upsertPlaylist_create_persistsWithGeneratedIdAndDefaults() {
        UpsertPlaylistDTO dto = new UpsertPlaylistDTO(Optional.empty(), "Chill Vibes", Optional.empty());

        ResponseEntity<ApiResponseDTO<String>> result = playlistService.upsertPlaylist(OWNER, dto);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        String id = result.getBody().data();
        assertThat(id).isNotBlank();

        Playlist saved = playlistRepository.findById(id).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Chill Vibes");
        assertThat(saved.getOwnerId()).isEqualTo(OWNER);
        assertThat(saved.getVisibility()).isEqualTo(PlaylistVisibility.PUBLIC);
        assertThat(saved.isSystem()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void upsertPlaylist_update_modifiesNameAndVisibility() {
        Playlist existing = playlistRepository.save(Playlist.builder()
                .name("Old Name")
                .ownerId(OWNER)
                .visibility(PlaylistVisibility.PUBLIC)
                .build());

        UpsertPlaylistDTO dto = new UpsertPlaylistDTO(
                Optional.of(existing.getId()), "New Name", Optional.of(PlaylistVisibility.PRIVATE)
        );

        playlistService.upsertPlaylist(OWNER, dto);

        Playlist reloaded = playlistRepository.findById(existing.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("New Name");
        assertThat(reloaded.getVisibility()).isEqualTo(PlaylistVisibility.PRIVATE);
    }

    @Test
    void createLikedSongsPlaylistForUser_isIdempotent_andUsesDeterministicId() {
        playlistService.createLikedSongsPlaylistForUser(OWNER);
        playlistService.createLikedSongsPlaylistForUser(OWNER);

        String expectedId = SystemPlaylistType.LIKED_SONGS.name() + "_" + OWNER;
        Playlist liked = playlistRepository.findById(expectedId).orElseThrow();

        assertThat(liked.isSystem()).isTrue();
        assertThat(liked.getSystemType()).isEqualTo(SystemPlaylistType.LIKED_SONGS);
        assertThat(liked.getVisibility()).isEqualTo(PlaylistVisibility.PRIVATE);
        assertThat(playlistRepository.findByOwnerId(OWNER)).hasSize(1);
    }

    @Test
    @Transactional
    void addSongToPlaylist_appendsToOrderedCollection_andDedupes() {
        Playlist playlist = playlistRepository.save(Playlist.builder()
                .name("Mix").ownerId(OWNER).visibility(PlaylistVisibility.PUBLIC).build());

        when(catalogPreviewClient.songExists("song-1")).thenReturn(true);
        when(catalogPreviewClient.songExists("song-2")).thenReturn(true);

        playlistService.addSongToPlaylist(OWNER, playlist.getId(), "song-1");
        playlistService.addSongToPlaylist(OWNER, playlist.getId(), "song-2");
        playlistService.addSongToPlaylist(OWNER, playlist.getId(), "song-1");

        Playlist reloaded = playlistRepository.findById(playlist.getId()).orElseThrow();
        assertThat(reloaded.getSongIds()).containsExactly("song-1", "song-2");
    }

    @Test
    void likePlaylist_incrementsLikeOrder_perUser() {
        Playlist p1 = playlistRepository.save(Playlist.builder()
                .name("A").ownerId(OTHER_USER).visibility(PlaylistVisibility.PUBLIC).build());
        Playlist p2 = playlistRepository.save(Playlist.builder()
                .name("B").ownerId(OTHER_USER).visibility(PlaylistVisibility.PUBLIC).build());
        Playlist p3 = playlistRepository.save(Playlist.builder()
                .name("C").ownerId(OTHER_USER).visibility(PlaylistVisibility.PUBLIC).build());

        playlistService.likePlaylist(OWNER, p1.getId());
        playlistService.likePlaylist(OWNER, p2.getId());
        playlistService.likePlaylist(OWNER, p3.getId());

        assertThat(likedPlaylistRepository.countByIdUserId(OWNER)).isEqualTo(3);
        assertThat(likedPlaylistRepository.findById(new LikedPlaylistId(OWNER, p1.getId())).orElseThrow().getLikeOrder()).isZero();
        assertThat(likedPlaylistRepository.findById(new LikedPlaylistId(OWNER, p3.getId())).orElseThrow().getLikeOrder()).isEqualTo(2);
    }

    @Test
    void likePlaylist_secondLike_isNoop() {
        Playlist p = playlistRepository.save(Playlist.builder()
                .name("X").ownerId(OTHER_USER).visibility(PlaylistVisibility.PUBLIC).build());

        playlistService.likePlaylist(OWNER, p.getId());
        playlistService.likePlaylist(OWNER, p.getId());

        assertThat(likedPlaylistRepository.countByIdUserId(OWNER)).isEqualTo(1);
    }

    @Test
    void getAllPlaylists_excludesSystemPlaylists_andOrdersByCreatedAtDesc() {
        playlistService.createLikedSongsPlaylistForUser(OWNER);
        playlistRepository.save(Playlist.builder()
                .name("Manual A").ownerId(OWNER).visibility(PlaylistVisibility.PUBLIC).build());
        playlistRepository.save(Playlist.builder()
                .name("Manual B").ownerId(OWNER).visibility(PlaylistVisibility.PUBLIC).build());

        when(userPreviewClient.getUserPreview(OWNER)).thenReturn(null);

        ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> result = playlistService.getAllPlaylists(OWNER);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<PlaylistPreviewDTO> playlists = result.getBody().data();
        assertThat(playlists).hasSize(2);
        assertThat(playlists).extracting(PlaylistPreviewDTO::name)
                .containsExactly("Manual B", "Manual A");
    }

    @Test
    void deletePlaylist_returns400_forSystemPlaylist() {
        playlistService.createLikedSongsPlaylistForUser(OWNER);
        String systemId = SystemPlaylistType.LIKED_SONGS.name() + "_" + OWNER;

        ResponseEntity<ApiResponseDTO<Void>> result = playlistService.deletePlaylist(OWNER, systemId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody().message()).isEqualTo("System playlists cannot be deleted");
        assertThat(playlistRepository.findById(systemId)).isPresent();
    }

    @Test
    void searchPlaylists_returnsOnlyPublic_filteredByName() {
        playlistRepository.save(Playlist.builder()
                .name("Workout Mix").ownerId(OTHER_USER).visibility(PlaylistVisibility.PUBLIC).build());
        playlistRepository.save(Playlist.builder()
                .name("Workout Private").ownerId(OTHER_USER).visibility(PlaylistVisibility.PRIVATE).build());
        playlistRepository.save(Playlist.builder()
                .name("Sleep Sounds").ownerId(OTHER_USER).visibility(PlaylistVisibility.PUBLIC).build());

        when(userPreviewClient.getUserPreview(OTHER_USER)).thenReturn(null);

        ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> result =
                playlistService.searchPlaylists(PageRequest.of(0, 10), "workout");

        List<PlaylistPreviewDTO> hits = result.getBody().data();
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).name()).isEqualTo("Workout Mix");
    }
}
