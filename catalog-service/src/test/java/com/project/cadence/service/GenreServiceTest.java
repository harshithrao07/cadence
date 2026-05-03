package com.project.cadence.service;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.PaginatedResponseDTO;
import com.project.cadence.dto.genre.GenrePreviewDTO;
import com.project.cadence.model.Genre;
import com.project.cadence.repository.GenreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenreServiceTest {

    @Mock private GenreRepository genreRepository;

    @InjectMocks
    private GenreService genreService;

    private Genre genre(String id, String type) {
        return Genre.builder().id(id).type(type).build();
    }

    // ── addNewGenre ───────────────────────────────────────────────────────────

    @Test
    void addNewGenre_returns201_andSavesUppercaseType_whenNew() {
        when(genreRepository.existsByType("HIP_HOP")).thenReturn(false);
        Genre saved = genre("g1", "HIP_HOP");
        when(genreRepository.save(any(Genre.class))).thenReturn(saved);

        ResponseEntity<ApiResponseDTO<String>> response = genreService.addNewGenre("hip_hop");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isEqualTo("g1");
    }

    @Test
    void addNewGenre_returns400_whenGenreAlreadyExists() {
        when(genreRepository.existsByType("ROCK")).thenReturn(true);

        ResponseEntity<ApiResponseDTO<String>> response = genreService.addNewGenre("rock");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("Genre already exists");
        verify(genreRepository, never()).save(any());
    }

    // ── getAllGenres ──────────────────────────────────────────────────────────

    @Test
    void getAllGenres_withoutKey_returnsAllGenres() {
        Genre g = genre("g1", "POP");
        when(genreRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(g), PageRequest.of(0, 10), 1));

        ResponseEntity<ApiResponseDTO<PaginatedResponseDTO<GenrePreviewDTO>>> response =
                genreService.getAllGenres(0, 10, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content()).hasSize(1);
        assertThat(response.getBody().data().content().get(0).type()).isEqualTo("POP");
    }

    @Test
    void getAllGenres_withKey_filtersGenresByPrefix() {
        Genre g = genre("g2", "JAZZ");
        when(genreRepository.findByTypeStartingWithIgnoreCase(anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(g), PageRequest.of(0, 10), 1));

        ResponseEntity<ApiResponseDTO<PaginatedResponseDTO<GenrePreviewDTO>>> response =
                genreService.getAllGenres(0, 10, "ja");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content()).hasSize(1);
        assertThat(response.getBody().data().content().get(0).type()).isEqualTo("JAZZ");
    }
}
