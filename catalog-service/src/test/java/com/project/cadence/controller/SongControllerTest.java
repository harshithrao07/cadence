package com.project.cadence.controller;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.filter.InternalTrafficFilter;
import com.project.cadence.service.SongService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SongController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = InternalTrafficFilter.class
        )
)
class SongControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean SongService songService;

    private static final String SONG_ID   = "song-1";
    private static final String RECORD_ID = "record-1";

    // ── GET /api/v1/song/{songId} ─────────────────────────────────────────────

    @Test
    void getSongById_returns200_forExistingSong() throws Exception {
        when(songService.getSongById(SONG_ID)).thenReturn(
                ResponseEntity.ok(new ApiResponseDTO<>(true, "Found", null))
        );

        mockMvc.perform(get("/api/v1/song/{id}", SONG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getSongById_returns404_forMissingSong() throws Exception {
        when(songService.getSongById("bad-song")).thenReturn(
                ResponseEntity.status(404).body(new ApiResponseDTO<>(false, "Song not found", null))
        );

        mockMvc.perform(get("/api/v1/song/{id}", "bad-song"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── GET /api/v1/song/all?recordId=... ─────────────────────────────────────

    @Test
    void getAllSongsByRecordId_returns200_forExistingRecord() throws Exception {
        when(songService.getAllSongsByRecordId(RECORD_ID)).thenReturn(
                ResponseEntity.ok(new ApiResponseDTO<>(true, "Found", List.of()))
        );

        mockMvc.perform(get("/api/v1/song/all").param("recordId", RECORD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getAllSongsByRecordId_returns400_forMissingRecord() throws Exception {
        when(songService.getAllSongsByRecordId("bad-record")).thenReturn(
                ResponseEntity.badRequest().body(new ApiResponseDTO<>(false, "Record not found", null))
        );

        mockMvc.perform(get("/api/v1/song/all").param("recordId", "bad-record"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getAllSongsByRecordId_returns400_whenRecordIdParamMissing() throws Exception {
        mockMvc.perform(get("/api/v1/song/all"))
                .andExpect(status().isBadRequest());
    }
}
