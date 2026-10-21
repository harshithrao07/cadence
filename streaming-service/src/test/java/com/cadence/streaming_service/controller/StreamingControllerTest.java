package com.cadence.streaming_service.controller;

import com.cadence.streaming_service.dto.ApiResponseDTO;
import com.cadence.streaming_service.dto.PlayHistoryDTO;
import com.cadence.streaming_service.dto.SongPlayCountDTO;
import com.cadence.streaming_service.dto.SongPlayStatsDTO;
import com.cadence.streaming_service.dto.SongStreamUrlDTO;
import com.cadence.streaming_service.filter.InternalTrafficFilter;
import com.cadence.streaming_service.service.PlayHistoryService;
import com.cadence.streaming_service.service.StreamingService;
import com.cadence.streaming_service.service.WorkerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = StreamingController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = InternalTrafficFilter.class
        )
)
class StreamingControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean PlayHistoryService playHistoryService;
    @MockBean StreamingService streamingService;
    @MockBean WorkerService workerService;

    private static final String USER_ID = "user-1";
    private static final String SONG_ID = "song-1";

    // ── GET /api/v1/stream/song/{songId} ──────────────────────────────────────

    @Test
    void getSongStreamUrl_returns200_withUrlAndExpiry() throws Exception {
        Instant expiresAt = Instant.parse("2026-01-01T01:00:00Z");
        when(streamingService.getStreamUrl(SONG_ID)).thenReturn(Optional.of(
                new SongStreamUrlDTO("https://signed.example/song-1", expiresAt)
        ));

        mockMvc.perform(get("/api/v1/stream/song/{id}", SONG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.url").value("https://signed.example/song-1"))
                .andExpect(jsonPath("$.data.expiresAt").exists());
    }

    @Test
    void getSongStreamUrl_returns404_whenSongHasNoAudio() throws Exception {
        when(streamingService.getStreamUrl(SONG_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/stream/song/{id}", SONG_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── POST /api/v1/stream/song/{songId}/plays ───────────────────────────────

    @Test
    void recordPlay_returns200_andRecordsForCaller() throws Exception {
        mockMvc.perform(post("/api/v1/stream/song/{id}/plays", SONG_ID).header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(workerService).recordPlay(USER_ID, SONG_ID);
    }

    @Test
    void recordPlay_returns400_whenUserIdHeaderMissing() throws Exception {
        mockMvc.perform(post("/api/v1/stream/song/{id}/plays", SONG_ID))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(workerService);
    }

    // ── GET /api/v1/stream/history ────────────────────────────────────────────

    @Test
    void getRecentHistory_returns200_withPlayHistory() throws Exception {
        when(playHistoryService.getRecentHistory(USER_ID, 0, 20)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/stream/history").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getRecentHistory_returns400_whenUserIdHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/v1/stream/history"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/stream/stats/users/me/top-songs ───────────────────────────

    @Test
    void getMyTopSongs_returns200_withUserId() throws Exception {
        when(playHistoryService.getUserTopSongs(USER_ID, 0, 20)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/stream/stats/users/me/top-songs").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ── GET /api/v1/stream/stats/trending ─────────────────────────────────────

    @Test
    void getTrendingSongs_returns200_withoutSinceParam() throws Exception {
        when(playHistoryService.getTrendingSongs(isNull(), eq(0), eq(20))).thenReturn(List.of(
                new SongPlayCountDTO(SONG_ID, 42L)
        ));

        mockMvc.perform(get("/api/v1/stream/stats/trending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].songId").value(SONG_ID))
                .andExpect(jsonPath("$.data[0].playCount").value(42));
    }

    @Test
    void getTrendingSongs_returns200_withPaginationParams() throws Exception {
        when(playHistoryService.getTrendingSongs(isNull(), eq(1), eq(5))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/stream/stats/trending")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk());
    }

    // ── GET /api/v1/stream/stats/songs/{songId} ───────────────────────────────

    @Test
    void getSongStats_returns200_withTotals() throws Exception {
        when(playHistoryService.getSongStats(SONG_ID)).thenReturn(
                new SongPlayStatsDTO(SONG_ID, 100L, 20L)
        );

        mockMvc.perform(get("/api/v1/stream/stats/songs/{id}", SONG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.songId").value(SONG_ID))
                .andExpect(jsonPath("$.data.totalPlays").value(100))
                .andExpect(jsonPath("$.data.totalListeners").value(20));
    }

    // ── GET /api/v1/stream/stats/songs/play-counts ────────────────────────────

    @Test
    void getSongPlayCounts_returns200_forSongIdList() throws Exception {
        when(playHistoryService.getSongPlayCounts(List.of(SONG_ID))).thenReturn(
                List.of(new SongPlayCountDTO(SONG_ID, 7L))
        );

        mockMvc.perform(get("/api/v1/stream/stats/songs/play-counts")
                        .param("songIds", SONG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].playCount").value(7));
    }

    // ── GET /api/v1/stream/stats/listeners ────────────────────────────────────

    @Test
    void getUniqueListeners_returns200_forSongIds() throws Exception {
        when(playHistoryService.getUniqueListeners(eq(List.of(SONG_ID)), any())).thenReturn(5L);

        mockMvc.perform(get("/api/v1/stream/stats/listeners")
                        .param("songIds", SONG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(5));
    }
}
