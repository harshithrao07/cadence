package com.project.cadence.service;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.PaginatedResponseDTO;
import com.project.cadence.dto.genre.GenrePreviewDTO;
import com.project.cadence.model.Genre;
import com.project.cadence.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class GenreService {
    private final GenreRepository genreRepository;

    public ResponseEntity<ApiResponseDTO<String>> addNewGenre(String type) {
        try {
            String genreType = type.toUpperCase(Locale.ROOT);
            if (genreRepository.existsByType(genreType)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponseDTO<>(false, "Genre already exists", null));
            }

            Genre genre = Genre.builder()
                    .type(genreType)
                    .build();
            Genre savedGenre = genreRepository.save(genre);

            return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseDTO<>(true, "Successfully created a new genre", savedGenre.getId()));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    public ResponseEntity<ApiResponseDTO<PaginatedResponseDTO<GenrePreviewDTO>>> getAllGenres(int page, int size, String key) {
        try {
            PageRequest pageable = PageRequest.of(page, size);
            Page<Genre> genres;

            if (key == null || key.isBlank()) {
                genres = genreRepository.findAll(pageable);
            } else {
                genres = genreRepository.findByTypeStartingWithIgnoreCase(key.trim(), pageable);
            }
            List<GenrePreviewDTO> genrePreviewDTOS = new ArrayList<>();
            genres.forEach(genre -> genrePreviewDTOS.add(new GenrePreviewDTO(
                    genre.getId(),
                    genre.getType()
            )));

            PaginatedResponseDTO<GenrePreviewDTO> response =
                    new PaginatedResponseDTO<>(
                            genrePreviewDTOS,
                            genres.getNumber(),
                            genres.getSize(),
                            genres.getTotalElements(),
                            genres.getTotalPages(),
                            genres.isLast()
                    );
            return ResponseEntity.ok(
                    new ApiResponseDTO<>(
                            true,
                            "Successfully retrieved artists",
                            response
                    )
            );
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }
}
