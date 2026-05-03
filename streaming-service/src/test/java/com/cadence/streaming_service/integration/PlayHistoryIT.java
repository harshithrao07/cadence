package com.cadence.streaming_service.integration;

import com.cadence.streaming_service.dto.PlayHistoryDTO;
import com.cadence.streaming_service.dto.SongPlayCountDTO;
import com.cadence.streaming_service.dto.SongPlayStatsDTO;
import com.cadence.streaming_service.model.PlayHistory;
import com.cadence.streaming_service.model.PlayHistoryId;
import com.cadence.streaming_service.repository.PlayHistoryRepository;
import com.cadence.streaming_service.service.PlayHistoryService;
import com.cadence.streaming_service.service.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlayHistoryIT extends BaseIntegrationTest {

    @Autowired WorkerService workerService;
    @Autowired PlayHistoryService playHistoryService;
    @Autowired PlayHistoryRepository playHistoryRepository;

    private static final String USER_A = "user-a";
    private static final String USER_B = "user-b";
    private static final String SONG_1 = "song-1";
    private static final String SONG_2 = "song-2";
    private static final String SONG_3 = "song-3";

    @BeforeEach
    void setUp() {
        playHistoryRepository.deleteAll();
    }

    @Test
    void recordPlay_createsRow_andIncrementsPlayCount_onRepeat() {
        workerService.recordPlay(USER_A, SONG_1);
        workerService.recordPlay(USER_A, SONG_1);
        workerService.recordPlay(USER_A, SONG_1);

        PlayHistory ph = playHistoryRepository.findById(new PlayHistoryId(USER_A, SONG_1)).orElseThrow();
        assertThat(ph.getPlayCount()).isEqualTo(3);
        assertThat(ph.getCreatedAt()).isNotNull();
        assertThat(ph.getLastPlayedAt()).isNotNull();
    }

    @Test
    void recordPlay_keepsPerUserPerSongRows_independent() {
        workerService.recordPlay(USER_A, SONG_1);
        workerService.recordPlay(USER_B, SONG_1);
        workerService.recordPlay(USER_A, SONG_2);

        assertThat(playHistoryRepository.count()).isEqualTo(3);
    }

    @Test
    void getRecentHistory_ordersByLastPlayedAtDesc() {
        workerService.recordPlay(USER_A, SONG_1);
        sleepShort();
        workerService.recordPlay(USER_A, SONG_2);
        sleepShort();
        workerService.recordPlay(USER_A, SONG_3);

        List<PlayHistoryDTO> history = playHistoryService.getRecentHistory(USER_A, 0, 10);

        assertThat(history).extracting(PlayHistoryDTO::songId).containsExactly(SONG_3, SONG_2, SONG_1);
    }

    @Test
    void getUserTopSongs_ordersByPlayCountDesc() {
        workerService.recordPlay(USER_A, SONG_1);
        workerService.recordPlay(USER_A, SONG_2);
        workerService.recordPlay(USER_A, SONG_2);
        workerService.recordPlay(USER_A, SONG_3);
        workerService.recordPlay(USER_A, SONG_3);
        workerService.recordPlay(USER_A, SONG_3);

        List<PlayHistoryDTO> top = playHistoryService.getUserTopSongs(USER_A, 0, 10);

        assertThat(top).extracting(PlayHistoryDTO::songId).containsExactly(SONG_3, SONG_2, SONG_1);
        assertThat(top).extracting(PlayHistoryDTO::playCount).containsExactly(3L, 2L, 1L);
    }

    @Test
    void getTrendingSongs_aggregatesAcrossUsers() {
        workerService.recordPlay(USER_A, SONG_1);
        workerService.recordPlay(USER_B, SONG_1);
        workerService.recordPlay(USER_B, SONG_1);
        workerService.recordPlay(USER_A, SONG_2);

        List<SongPlayCountDTO> trending = playHistoryService.getTrendingSongs(0, 10);

        assertThat(trending).extracting(SongPlayCountDTO::songId).containsExactly(SONG_1, SONG_2);
        assertThat(trending.get(0).playCount()).isEqualTo(3L);
        assertThat(trending.get(1).playCount()).isEqualTo(1L);
    }

    @Test
    void getTrendingSongsSince_filtersByTime() {
        workerService.recordPlay(USER_A, SONG_1);
        Instant cutoff = Instant.now().plus(1, ChronoUnit.SECONDS);
        sleepBeyondCutoff();
        workerService.recordPlay(USER_A, SONG_2);
        workerService.recordPlay(USER_B, SONG_2);

        List<SongPlayCountDTO> recent = playHistoryService.getTrendingSongs(cutoff, 0, 10);

        assertThat(recent).extracting(SongPlayCountDTO::songId).containsExactly(SONG_2);
        assertThat(recent.get(0).playCount()).isEqualTo(2L);
    }

    @Test
    void getSongStats_returnsTotalPlays_andUniqueListenerCount() {
        workerService.recordPlay(USER_A, SONG_1);
        workerService.recordPlay(USER_A, SONG_1);
        workerService.recordPlay(USER_B, SONG_1);

        SongPlayStatsDTO stats = playHistoryService.getSongStats(SONG_1);

        assertThat(stats.songId()).isEqualTo(SONG_1);
        assertThat(stats.totalPlays()).isEqualTo(3L);
        assertThat(stats.totalListeners()).isEqualTo(2L);
    }

    @Test
    void getSongStats_returnsZeros_forUnknownSong() {
        SongPlayStatsDTO stats = playHistoryService.getSongStats("unknown-song");

        assertThat(stats.totalPlays()).isZero();
        assertThat(stats.totalListeners()).isZero();
    }

    @Test
    void getSongPlayCounts_returnsAggregatedCounts_perSongInList() {
        workerService.recordPlay(USER_A, SONG_1);
        workerService.recordPlay(USER_B, SONG_1);
        workerService.recordPlay(USER_A, SONG_2);

        List<SongPlayCountDTO> counts = playHistoryService.getSongPlayCounts(List.of(SONG_1, SONG_2, SONG_3));

        assertThat(counts).hasSize(2);
        assertThat(counts).extracting(SongPlayCountDTO::songId).containsExactlyInAnyOrder(SONG_1, SONG_2);
    }

    @Test
    void getUniqueListeners_returnsZero_forEmptyList() {
        assertThat(playHistoryService.getUniqueListeners(List.of(), null)).isZero();
    }

    @Test
    void getUniqueListeners_countsDistinctListeners_acrossSongList() {
        workerService.recordPlay(USER_A, SONG_1);
        workerService.recordPlay(USER_B, SONG_1);
        workerService.recordPlay(USER_A, SONG_2);

        long listeners = playHistoryService.getUniqueListeners(List.of(SONG_1, SONG_2), null);

        assertThat(listeners).isEqualTo(2L);
    }

    private static void sleepShort() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepBeyondCutoff() {
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
