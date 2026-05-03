package com.cadence.streaming_service.service;

import com.cadence.streaming_service.dto.PlayHistoryDTO;
import com.cadence.streaming_service.dto.SongPlayCountDTO;
import com.cadence.streaming_service.dto.SongPlayStatsDTO;
import com.cadence.streaming_service.model.PlayHistory;
import com.cadence.streaming_service.model.PlayHistoryId;
import com.cadence.streaming_service.repository.PlayHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayHistoryServiceTest {

    @Mock private PlayHistoryRepository playHistoryRepository;

    @InjectMocks
    private PlayHistoryService playHistoryService;

    private static final String USER_ID = "user-1";
    private static final String SONG_ID = "song-1";

    private PlayHistory playHistory(String userId, String songId, long playCount) {
        return PlayHistory.builder()
                .id(new PlayHistoryId(userId, songId))
                .playCount(playCount)
                .build();
    }

    private PlayHistoryRepository.SongPlayCountProjection projection(String songId, long playCount) {
        return new PlayHistoryRepository.SongPlayCountProjection() {
            @Override public String getSongId() { return songId; }
            @Override public long getPlayCount() { return playCount; }
        };
    }

    // ── getRecentHistory ──────────────────────────────────────────────────────

    @Test
    void getRecentHistory_returnsMappedDTOs() {
        PlayHistory ph = playHistory(USER_ID, SONG_ID, 5L);
        when(playHistoryRepository.findByIdUserIdOrderByLastPlayedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of(ph));

        List<PlayHistoryDTO> result = playHistoryService.getRecentHistory(USER_ID, 0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(USER_ID);
        assertThat(result.get(0).songId()).isEqualTo(SONG_ID);
        assertThat(result.get(0).playCount()).isEqualTo(5L);
    }

    @Test
    void getRecentHistory_returnsEmpty_whenNoHistory() {
        when(playHistoryRepository.findByIdUserIdOrderByLastPlayedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of());

        List<PlayHistoryDTO> result = playHistoryService.getRecentHistory(USER_ID, 0, 10);

        assertThat(result).isEmpty();
    }

    // ── getUserTopSongs ───────────────────────────────────────────────────────

    @Test
    void getUserTopSongs_returnsMappedDTOs() {
        PlayHistory ph = playHistory(USER_ID, SONG_ID, 10L);
        when(playHistoryRepository.findByIdUserIdOrderByPlayCountDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of(ph));

        List<PlayHistoryDTO> result = playHistoryService.getUserTopSongs(USER_ID, 0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).playCount()).isEqualTo(10L);
    }

    // ── getTrendingSongs ──────────────────────────────────────────────────────

    @Test
    void getTrendingSongs_withoutSince_returnsMappedDTOs() {
        when(playHistoryRepository.findTrendingSongs(any(Pageable.class)))
                .thenReturn(List.of(projection(SONG_ID, 42L)));

        List<SongPlayCountDTO> result = playHistoryService.getTrendingSongs(0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).songId()).isEqualTo(SONG_ID);
        assertThat(result.get(0).playCount()).isEqualTo(42L);
    }

    @Test
    void getTrendingSongs_withNullSince_delegatesToNoFilterVariant() {
        when(playHistoryRepository.findTrendingSongs(any(Pageable.class)))
                .thenReturn(List.of(projection(SONG_ID, 7L)));

        List<SongPlayCountDTO> result = playHistoryService.getTrendingSongs(null, 0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).songId()).isEqualTo(SONG_ID);
    }

    @Test
    void getTrendingSongs_withSince_filtersFromTimestamp() {
        Instant since = Instant.now().minusSeconds(86400);
        when(playHistoryRepository.findTrendingSongsSince(eq(since), any(Pageable.class)))
                .thenReturn(List.of(projection(SONG_ID, 3L)));

        List<SongPlayCountDTO> result = playHistoryService.getTrendingSongs(since, 0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).playCount()).isEqualTo(3L);
    }

    // ── getSongStats ──────────────────────────────────────────────────────────

    @Test
    void getSongStats_returnsAggregatedStatsForSong() {
        when(playHistoryRepository.getTotalPlaysBySongId(SONG_ID)).thenReturn(100L);
        when(playHistoryRepository.countByIdSongId(SONG_ID)).thenReturn(20L);

        SongPlayStatsDTO stats = playHistoryService.getSongStats(SONG_ID);

        assertThat(stats.songId()).isEqualTo(SONG_ID);
        assertThat(stats.totalPlays()).isEqualTo(100L);
        assertThat(stats.totalListeners()).isEqualTo(20L);
    }

    // ── getSongPlayCounts ─────────────────────────────────────────────────────

    @Test
    void getSongPlayCounts_returnsEmptyList_whenInputIsEmpty() {
        List<SongPlayCountDTO> result = playHistoryService.getSongPlayCounts(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void getSongPlayCounts_returnsMappedCounts_forNonEmptyInput() {
        List<String> songIds = List.of(SONG_ID);
        when(playHistoryRepository.findPlayCountsForSongs(songIds))
                .thenReturn(List.of(projection(SONG_ID, 15L)));

        List<SongPlayCountDTO> result = playHistoryService.getSongPlayCounts(songIds);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).songId()).isEqualTo(SONG_ID);
        assertThat(result.get(0).playCount()).isEqualTo(15L);
    }

    // ── getUniqueListeners ────────────────────────────────────────────────────

    @Test
    void getUniqueListeners_returnsZero_whenSongIdsIsEmpty() {
        long result = playHistoryService.getUniqueListeners(List.of(), Instant.now());

        assertThat(result).isZero();
    }

    @Test
    void getUniqueListeners_returnsCount_forValidSongIds() {
        Instant since = Instant.now().minusSeconds(3600);
        List<String> songIds = List.of(SONG_ID);
        when(playHistoryRepository.countDistinctListenersForSongs(songIds, since)).thenReturn(5L);

        long result = playHistoryService.getUniqueListeners(songIds, since);

        assertThat(result).isEqualTo(5L);
    }
}
