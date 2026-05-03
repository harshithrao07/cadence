package com.project.cadence.integration;

import com.project.cadence.client.PlaylistClient;
import com.project.cadence.client.StreamingStatsClient;
import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.generic.DiscoverDTO;
import com.project.cadence.dto.generic.GlobalSearchDTO;
import com.project.cadence.dto.internal.PlayHistoryDTO;
import com.project.cadence.dto.internal.SongPlayCountDTO;
import com.project.cadence.dto.playlist.PlaylistPreviewDTO;
import com.project.cadence.dto.record.UpsertRecordDTO;
import com.project.cadence.dto.song.UpsertSongDTO;
import com.project.cadence.model.Artist;
import com.project.cadence.model.Genre;
import com.project.cadence.model.Record;
import com.project.cadence.model.RecordType;
import com.project.cadence.model.Song;
import com.project.cadence.repository.ArtistRepository;
import com.project.cadence.repository.GenreRepository;
import com.project.cadence.repository.RecordRepository;
import com.project.cadence.repository.SongRepository;
import com.project.cadence.service.AwsService;
import com.project.cadence.service.GenericService;
import com.project.cadence.service.RecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class GenericServiceIT extends BaseIntegrationTest {

    @Autowired GenericService genericService;
    @Autowired RecordService recordService;
    @Autowired ArtistRepository artistRepository;
    @Autowired GenreRepository genreRepository;
    @Autowired RecordRepository recordRepository;
    @Autowired SongRepository songRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockBean PlaylistClient playlistClient;
    @MockBean StreamingStatsClient streamingStatsClient;
    @MockBean AwsService awsService;

    private Artist drake;
    private Artist kendrick;
    private Genre hipHop;
    private Genre rb;
    private Record certifiedLoverBoy;
    private Song godsPlan;
    private Song niceForWhat;
    private static final String USER_ID = "user-1";

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of(
                "artist_following", "users",
                "artist_created_songs", "artist_records", "song_genre",
                "song", "record", "artist", "genre"
        )) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        drake = artistRepository.save(Artist.builder().name("Drake").build());
        kendrick = artistRepository.save(Artist.builder().name("Kendrick").build());
        hipHop = genreRepository.save(Genre.builder().type("HIP_HOP").build());
        rb = genreRepository.save(Genre.builder().type("R_B").build());

        when(awsService.getUrl(anyString())).thenAnswer(inv -> "https://cdn.test/" + inv.getArgument(0));
        when(awsService.getPresignedUrl(anyString(), anyString(), anyString(), any())).thenReturn("https://signed.test/");

        UpsertRecordDTO dto = new UpsertRecordDTO(
                Optional.empty(), "Certified Lover Boy", System.currentTimeMillis(),
                RecordType.ALBUM, List.of(drake.getId()),
                List.of(
                        new UpsertSongDTO(Optional.empty(), "God's Plan", Set.of(hipHop.getId()), List.of(drake.getId()), 180),
                        new UpsertSongDTO(Optional.empty(), "Nice For What", Set.of(hipHop.getId(), rb.getId()), List.of(drake.getId()), 180)
                )
        );
        var resp = recordService.upsertNewRecord(dto);
        String recordId = resp.getBody().data().id();
        certifiedLoverBoy = recordRepository.findById(recordId).orElseThrow();
        // Pull song IDs out of the upsert response — RecordService already returns them
        // in the order they were submitted, avoiding lazy-collection access on the Record.
        List<com.project.cadence.dto.song.SongResponseDTO> songResponses = resp.getBody().data().songs();
        godsPlan = Song.builder().id(songResponses.get(0).id()).build();
        niceForWhat = Song.builder().id(songResponses.get(1).id()).build();
    }

    // ── getSearchResponse ──────────────────────────────────────────────────

    @Test
    void getSearchResponse_returns200_andCallsPlaylistClient_andCollectsAllEntities() {
        when(playlistClient.searchPlaylists(eq(0), eq(20), eq("dr"))).thenReturn(List.of(
                new PlaylistPreviewDTO("p-1", "Drake Mix", null, null, null, false, null, null)
        ));

        ResponseEntity<ApiResponseDTO<GlobalSearchDTO>> result = genericService.getSearchResponse(0, 20, "dr");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        GlobalSearchDTO data = result.getBody().data();
        assertThat(data).isNotNull();
        assertThat(data.artists()).extracting("name").contains("Drake");
        assertThat(data.playlists()).extracting("id").containsExactly("p-1");
    }

    @Test
    void getSearchResponse_returns200_withEmptyResults_whenNoMatches() {
        when(playlistClient.searchPlaylists(anyInt(), anyInt(), anyString())).thenReturn(List.of());

        ResponseEntity<ApiResponseDTO<GlobalSearchDTO>> result = genericService.getSearchResponse(0, 20, "no-match");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data().artists()).isEmpty();
        assertThat(result.getBody().data().records()).isEmpty();
    }

    // ── getDiscoveryFeed ───────────────────────────────────────────────────

    @Test
    void getDiscoveryFeed_returns404_whenUserDoesNotExist() {
        ResponseEntity<ApiResponseDTO<DiscoverDTO>> result = genericService.getDiscoveryFeed("ghost-user");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody().message()).isEqualTo("User cannot be found");
    }

    @Test
    @Transactional
    void getDiscoveryFeed_returnsAllSections_withSeededDataAndStubbedExternalCalls() {
        seedUser(USER_ID);
        seedFollowing(USER_ID, drake.getId());

        when(streamingStatsClient.getTrendingSongs(any(Instant.class), eq(0), eq(20))).thenReturn(List.of(
                new SongPlayCountDTO(godsPlan.getId(), 100L),
                new SongPlayCountDTO(niceForWhat.getId(), 50L)
        ));
        when(streamingStatsClient.getUserTopSongs(eq(USER_ID), eq(0), eq(20))).thenReturn(List.of(
                new PlayHistoryDTO(USER_ID, godsPlan.getId(), 5, null, null)
        ));
        when(streamingStatsClient.getRecentHistory(eq(USER_ID), eq(0), eq(15))).thenReturn(List.of(
                new PlayHistoryDTO(USER_ID, niceForWhat.getId(), 1, null, null)
        ));

        ResponseEntity<ApiResponseDTO<DiscoverDTO>> result = genericService.getDiscoveryFeed(USER_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        DiscoverDTO d = result.getBody().data();
        assertThat(d).isNotNull();
        assertThat(d.trendingSongs()).extracting("id")
                .containsExactly(godsPlan.getId(), niceForWhat.getId());
        assertThat(d.popularArtists()).extracting("name").contains("Drake");
        assertThat(d.newReleases()).extracting("title").contains("Certified Lover Boy");
        assertThat(d.newReleasesOfFollowingArtists()).extracting("title")
                .contains("Certified Lover Boy");
        assertThat(d.recentlyPlayedSongs()).extracting("id").containsExactly(niceForWhat.getId());
        // recommendedSongs / suggestedArtists are non-empty because userTopSongs is non-empty
        assertThat(d.recommendedSongs()).isNotNull();
        assertThat(d.suggestedArtists()).isNotNull();
    }

    @Test
    @Transactional
    void getDiscoveryFeed_skipsFollowedArtistsBranch_whenUserFollowsNoOne() {
        seedUser(USER_ID);

        when(streamingStatsClient.getTrendingSongs(any(Instant.class), anyInt(), anyInt())).thenReturn(List.of());
        when(streamingStatsClient.getUserTopSongs(anyString(), anyInt(), anyInt())).thenReturn(List.of());
        when(streamingStatsClient.getRecentHistory(anyString(), anyInt(), anyInt())).thenReturn(List.of());

        ResponseEntity<ApiResponseDTO<DiscoverDTO>> result = genericService.getDiscoveryFeed(USER_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        DiscoverDTO d = result.getBody().data();
        assertThat(d.newReleasesOfFollowingArtists()).isEmpty();
        assertThat(d.recommendedSongs()).isEmpty();
        assertThat(d.suggestedArtists()).isEmpty();
    }

    @Test
    @Transactional
    void getDiscoveryFeed_skipsRecommendedBranch_whenTopSongsIsEmpty() {
        seedUser(USER_ID);
        seedFollowing(USER_ID, drake.getId());

        when(streamingStatsClient.getTrendingSongs(any(Instant.class), anyInt(), anyInt())).thenReturn(List.of());
        when(streamingStatsClient.getUserTopSongs(anyString(), anyInt(), anyInt())).thenReturn(List.of());
        when(streamingStatsClient.getRecentHistory(anyString(), anyInt(), anyInt())).thenReturn(List.of());

        ResponseEntity<ApiResponseDTO<DiscoverDTO>> result = genericService.getDiscoveryFeed(USER_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        DiscoverDTO d = result.getBody().data();
        // followedArtists path was taken (newReleasesOfFollowingArtists has values),
        // but recommendedSongs/suggestedArtists are empty since topGenres is empty
        assertThat(d.newReleasesOfFollowingArtists()).isNotEmpty();
        assertThat(d.recommendedSongs()).isEmpty();
        assertThat(d.suggestedArtists()).isEmpty();
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private Record persistRecordWithSongs(String title, List<Artist> artists, List<Song> songs) {
        Record r = Record.builder()
                .title(title)
                .releaseTimestamp(System.currentTimeMillis())
                .recordType(RecordType.ALBUM)
                .build();
        r.setArtists(new ArrayList<>(artists));
        for (Song s : songs) {
            s.setRecord(r);
            r.getSongs().add(s);
        }
        return recordRepository.save(r);
    }

    private static Song songWith(String title, List<Artist> creators, Set<Genre> genres) {
        return Song.builder()
                .title(title)
                .totalDuration(180)
                .createdBy(new ArrayList<>(creators))
                .genres(genres)
                .build();
    }

    private void seedUser(String userId) {
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, profile_url) VALUES (?, ?, ?, ?)",
                userId, "Alice", "alice@example.com", null
        );
    }

    private void seedFollowing(String userId, String artistId) {
        jdbcTemplate.update(
                "INSERT INTO artist_following (user_id, artist_id, follow_order) VALUES (?, ?, ?)",
                userId, artistId, 0
        );
    }
}
