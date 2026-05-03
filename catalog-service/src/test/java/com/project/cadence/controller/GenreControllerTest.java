package com.project.cadence.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.PaginatedResponseDTO;
import com.project.cadence.dto.genre.GenrePreviewDTO;
import com.project.cadence.dto.genre.NewGenreDTO;
import com.project.cadence.filter.InternalTrafficFilter;
import com.project.cadence.service.GenreService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = GenreController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = InternalTrafficFilter.class
        )
)
class GenreControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean GenreService genreService;

    // ── POST /api/v1/genre/add ────────────────────────────────────────────────

    @Test
    void addNewGenre_returns201_onSuccess() throws Exception {
        NewGenreDTO dto = new NewGenreDTO("JAZZ");
        when(genreService.addNewGenre("JAZZ")).thenReturn(
                ResponseEntity.status(201).body(new ApiResponseDTO<>(true, "Created", "genre-1"))
        );

        mockMvc.perform(post("/api/v1/genre/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("genre-1"));
    }

    @Test
    void addNewGenre_returns400_whenTypeIsBlank() throws Exception {
        NewGenreDTO dto = new NewGenreDTO("");

        mockMvc.perform(post("/api/v1/genre/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addNewGenre_returns400_whenGenreAlreadyExists() throws Exception {
        NewGenreDTO dto = new NewGenreDTO("ROCK");
        when(genreService.addNewGenre("ROCK")).thenReturn(
                ResponseEntity.badRequest().body(new ApiResponseDTO<>(false, "Genre already exists", null))
        );

        mockMvc.perform(post("/api/v1/genre/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Genre already exists"));
    }

    // ── GET /api/v1/genre/all ─────────────────────────────────────────────────

    @Test
    void getAllGenres_returns200_withDefaultPagination() throws Exception {
        PaginatedResponseDTO<GenrePreviewDTO> page = new PaginatedResponseDTO<>(List.of(), 0, 24, 0L, 0, true);
        when(genreService.getAllGenres(0, 24, null)).thenReturn(
                ResponseEntity.ok(new ApiResponseDTO<>(true, "ok", page))
        );

        mockMvc.perform(get("/api/v1/genre/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getAllGenres_returns200_withKeyFilter() throws Exception {
        GenrePreviewDTO jazz = new GenrePreviewDTO("g1", "JAZZ");
        PaginatedResponseDTO<GenrePreviewDTO> page = new PaginatedResponseDTO<>(List.of(jazz), 0, 24, 1L, 1, true);
        when(genreService.getAllGenres(0, 24, "ja")).thenReturn(
                ResponseEntity.ok(new ApiResponseDTO<>(true, "ok", page))
        );

        mockMvc.perform(get("/api/v1/genre/all").param("key", "ja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].type").value("JAZZ"));
    }
}
