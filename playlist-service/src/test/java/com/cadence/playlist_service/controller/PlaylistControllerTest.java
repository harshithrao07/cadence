package com.cadence.playlist_service.controller;

import com.cadence.playlist_service.dto.ApiResponseDTO;
import com.cadence.playlist_service.dto.PlaylistPreviewDTO;
import com.cadence.playlist_service.dto.UpsertPlaylistDTO;
import com.cadence.playlist_service.filter.InternalTrafficFilter;
import com.cadence.playlist_service.model.PlaylistVisibility;
import com.cadence.playlist_service.service.PlaylistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PlaylistController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = InternalTrafficFilter.class
        )
)
class PlaylistControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean PlaylistService playlistService;

    private static final String USER_ID      = "user-1";
    private static final String PLAYLIST_ID  = "playlist-1";
    private static final String SONG_ID      = "song-1";

    // ── GET /api/v1/playlist/all ──────────────────────────────────────────────

    @Test
    void getAllPlaylists_returns200_withUserIdHeader() throws Exception {
        when(playlistService.getAllPlaylists(USER_ID)).thenReturn(
                ResponseEntity.ok(new ApiResponseDTO<>(true, "ok", List.of()))
        );

        mockMvc.perform(get("/api/v1/playlist/all").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getAllPlaylists_returns400_whenUserIdHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/v1/playlist/all"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/playlist/{playlistId} ─────────────────────────────────────

    @Test
    void getPlaylist_returns200_forExistingPlaylist() throws Exception {
        when(playlistService.getPlaylist(eq(USER_ID), eq(PLAYLIST_ID))).thenReturn(
                ResponseEntity.ok(new ApiResponseDTO<>(true, "ok", null))
        );

        mockMvc.perform(get("/api/v1/playlist/{id}", PLAYLIST_ID).header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getPlaylist_returns404_forMissingPlaylist() throws Exception {
        when(playlistService.getPlaylist(eq(USER_ID), eq(PLAYLIST_ID))).thenReturn(
                ResponseEntity.status(404).body(new ApiResponseDTO<>(false, "Not found", null))
        );

        mockMvc.perform(get("/api/v1/playlist/{id}", PLAYLIST_ID).header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── POST /api/v1/playlist/upsert ──────────────────────────────────────────

    @Test
    void upsertPlaylist_returns200_withValidBody() throws Exception {
        UpsertPlaylistDTO dto = new UpsertPlaylistDTO(Optional.empty(), "My Playlist", Optional.of(PlaylistVisibility.PUBLIC));
        when(playlistService.upsertPlaylist(eq(USER_ID), any())).thenReturn(
                ResponseEntity.ok(new ApiResponseDTO<>(true, "Created", PLAYLIST_ID))
        );

        mockMvc.perform(post("/api/v1/playlist/upsert")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(PLAYLIST_ID));
    }

    @Test
    void upsertPlaylist_returns400_whenNameIsBlank() throws Exception {
        UpsertPlaylistDTO dto = new UpsertPlaylistDTO(Optional.empty(), "", Optional.empty());

        mockMvc.perform(post("/api/v1/playlist/upsert")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /api/v1/playlist/{playlistId}/song/{songId} ───────────────────────

    @Test
    void addSongToPlaylist_returns200_onSuccess() throws Exception {
        when(playlistService.addSongToPlaylist(USER_ID, PLAYLIST_ID, SONG_ID)).thenReturn(
                ResponseEntity.ok(new ApiResponseDTO<>(true, "Added", null))
        );

        mockMvc.perform(put("/api/v1/playlist/{pid}/song/{sid}", PLAYLIST_ID, SONG_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── DELETE /api/v1/playlist/{playlistId} ──────────────────────────────────

    @Test
    void deletePlaylist_returns200_onSuccess() throws Exception {
        when(playlistService.deletePlaylist(USER_ID, PLAYLIST_ID)).thenReturn(
                ResponseEntity.ok(new ApiResponseDTO<>(true, "Deleted", null))
        );

        mockMvc.perform(delete("/api/v1/playlist/{id}", PLAYLIST_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── GET /api/v1/playlist/search ───────────────────────────────────────────

    @Test
    void searchPlaylists_returns200_withOptionalKey() throws Exception {
        when(playlistService.searchPlaylists(any(), eq("rock"))).thenReturn(
                ResponseEntity.ok(new ApiResponseDTO<>(true, "ok", List.of()))
        );

        mockMvc.perform(get("/api/v1/playlist/search").param("key", "rock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
