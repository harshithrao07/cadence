package com.project.cadence.service;

import com.project.cadence.client.PlaylistClient;
import com.project.cadence.client.StreamingStatsClient;
import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.artist.ArtistPreviewDTO;
import com.project.cadence.dto.generic.DiscoverDTO;
import com.project.cadence.dto.generic.GlobalSearchDTO;
import com.project.cadence.dto.genre.GenrePreviewDTO;
import com.project.cadence.dto.internal.PlayHistoryDTO;
import com.project.cadence.dto.internal.SongPlayCountDTO;
import com.project.cadence.dto.record.RecordPreviewDTO;
import com.project.cadence.dto.record.RecordPreviewWithCoverImageDTO;
import com.project.cadence.dto.song.EachSongDTO;
import com.project.cadence.dto.song.SongBaseDTO;
import com.project.cadence.model.Record;
import com.project.cadence.repository.ArtistRepository;
import com.project.cadence.repository.RecordRepository;
import com.project.cadence.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenericService {
    private final ArtistService artistService;
    private final RecordService recordService;
    private final SongService songService;
    private final PlaylistClient playlistClient;
    private final SongRepository songRepository;
    private final ArtistRepository artistRepository;
    private final RecordRepository recordRepository;
    private final StreamingStatsClient streamingStatsClient;
    private final JdbcTemplate jdbcTemplate;

    public ResponseEntity<ApiResponseDTO<GlobalSearchDTO>> getSearchResponse(int page, int size, String key) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            GlobalSearchDTO globalSearchDTO = new GlobalSearchDTO(
                    artistService.getArtistsForSearch(pageable, key),
                    recordService.getRecordsForSearch(pageable, key),
                    songService.getRecordsForSearch(pageable, key),
                    playlistClient.searchPlaylists(page, size, key)
            );
            return ResponseEntity.ok(new ApiResponseDTO<>(true, "Successfully retrieved data", globalSearchDTO));
        } catch (Exception e) {
            log.error("An exception has occurred", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    public ResponseEntity<ApiResponseDTO<DiscoverDTO>> getDiscoveryFeed(String userId) {
        try {
            if (!userExists(userId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponseDTO<>(false, "User cannot be found", null));
            }

            Instant lastWeek = Instant.now().minus(7, ChronoUnit.DAYS);

            List<String> trendingSongIds = streamingStatsClient.getTrendingSongs(lastWeek, 0, 20)
                    .stream()
                    .map(SongPlayCountDTO::songId)
                    .toList();
            List<SongBaseDTO> trendingBase = findBaseSongsInOrder(trendingSongIds);
            List<EachSongDTO> trendingSongs = enrichSongs(trendingBase);

            List<ArtistPreviewDTO> popularArtists = artistRepository.findPopularArtists(PageRequest.of(0, 10));

            List<RecordPreviewDTO> newReleases = recordRepository.findNewReleases(PageRequest.of(0, 12))
                    .stream()
                    .map(this::toRecordPreview)
                    .toList();

            List<String> followedArtistIds = getFollowedArtistIds(userId);
            List<RecordPreviewDTO> newReleasesOfFollowingArtists = List.of();
            if (!followedArtistIds.isEmpty()) {
                newReleasesOfFollowingArtists =
                        recordRepository.findNewReleasesFromFollowedArtists(
                                        followedArtistIds,
                                        PageRequest.of(0, 12)
                                )
                                .stream()
                                .map(this::toRecordPreview)
                                .toList();
            }

            List<String> topSongIds = streamingStatsClient.getUserTopSongs(userId, 0, 20)
                    .stream()
                    .map(PlayHistoryDTO::songId)
                    .toList();
            List<String> topGenres = topSongIds.isEmpty()
                    ? List.of()
                    : songRepository.findGenreIdsForSongs(topSongIds).stream().limit(3).toList();

            List<EachSongDTO> recommendedSongs;
            List<ArtistPreviewDTO> suggestedArtists;
            if (topGenres.isEmpty()) {
                recommendedSongs = List.of();
                suggestedArtists = List.of();
            } else {
                List<SongBaseDTO> recommendedBase =
                        songRepository.findRecommendedFromGenres(
                                topGenres,
                                topSongIds.isEmpty() ? List.of("-1") : topSongIds,
                                PageRequest.of(0, 20)
                        );

                recommendedSongs = enrichSongs(recommendedBase);
                suggestedArtists = artistRepository.findSuggestedArtists(
                        topGenres,
                        followedArtistIds.isEmpty() ? List.of("-1") : followedArtistIds,
                        PageRequest.of(0, 10)
                );
            }

            List<String> recentSongIds = streamingStatsClient.getRecentHistory(userId, 0, 15)
                    .stream()
                    .map(PlayHistoryDTO::songId)
                    .toList();
            List<SongBaseDTO> recentBase = findBaseSongsInOrder(recentSongIds);
            List<EachSongDTO> recentlyPlayed = enrichSongs(recentBase);

            DiscoverDTO discoverDTO = new DiscoverDTO(
                    trendingSongs,
                    popularArtists,
                    recommendedSongs,
                    newReleases,
                    newReleasesOfFollowingArtists,
                    recentlyPlayed,
                    suggestedArtists
            );

            return ResponseEntity.ok(new ApiResponseDTO<>(true, "Successfully retrieved data", discoverDTO));
        } catch (Exception e) {
            log.error("An exception has occurred", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    private List<EachSongDTO> enrichSongs(List<SongBaseDTO> baseSongs) {
        if (baseSongs.isEmpty()) {
            return List.of();
        }

        List<String> songIds = baseSongs.stream()
                .map(SongBaseDTO::id)
                .toList();

        List<Object[]> artistRows = songRepository.findArtistsForSongs(songIds);
        Map<String, List<ArtistPreviewDTO>> artistMap = new HashMap<>();
        for (Object[] row : artistRows) {
            String songId = (String) row[0];
            ArtistPreviewDTO artist = (ArtistPreviewDTO) row[1];
            artistMap.computeIfAbsent(songId, ignored -> new ArrayList<>()).add(artist);
        }

        List<Object[]> genreRows = songRepository.findGenresForSongs(songIds);
        Map<String, List<GenrePreviewDTO>> genreMap = new HashMap<>();
        for (Object[] row : genreRows) {
            String songId = (String) row[0];
            GenrePreviewDTO genre = (GenrePreviewDTO) row[1];
            genreMap.computeIfAbsent(songId, ignored -> new ArrayList<>()).add(genre);
        }

        return baseSongs.stream()
                .map(base -> new EachSongDTO(
                        base.id(),
                        base.title(),
                        base.totalDuration(),
                        artistMap.getOrDefault(base.id(), List.of()),
                        genreMap.getOrDefault(base.id(), List.of()),
                        new RecordPreviewWithCoverImageDTO(
                                base.recordId(),
                                base.recordTitle(),
                                base.coverUrl(),
                                0
                        )
                ))
                .toList();
    }

    private List<SongBaseDTO> findBaseSongsInOrder(List<String> songIds) {
        if (songIds.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> order = new HashMap<>();
        for (int i = 0; i < songIds.size(); i++) {
            order.put(songIds.get(i), i);
        }

        return songRepository.findBaseSongsByIds(songIds)
                .stream()
                .sorted((first, second) -> Integer.compare(
                        order.getOrDefault(first.id(), Integer.MAX_VALUE),
                        order.getOrDefault(second.id(), Integer.MAX_VALUE)
                ))
                .toList();
    }

    private RecordPreviewDTO toRecordPreview(Record record) {
        return new RecordPreviewDTO(
                record.getId(),
                record.getTitle(),
                record.getReleaseTimestamp(),
                record.getCoverUrl(),
                record.getRecordType(),
                record.getArtists().stream()
                        .map(artist -> new ArtistPreviewDTO(
                                artist.getId(),
                                artist.getName(),
                                artist.getProfileUrl()
                        ))
                        .toList()
        );
    }

    private boolean userExists(String userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class,
                userId
        );

        return count != null && count > 0;
    }

    private List<String> getFollowedArtistIds(String userId) {
        return jdbcTemplate.queryForList(
                "SELECT artist_id FROM artist_following WHERE user_id = ? ORDER BY follow_order ASC",
                String.class,
                userId
        );
    }
}
