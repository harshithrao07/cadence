package com.project.cadence.controller;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.PaginatedResponseDTO;
import com.project.cadence.dto.genre.GenrePreviewDTO;
import com.project.cadence.dto.genre.NewGenreDTO;
import com.project.cadence.service.GenreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/genre")
public class GenreController {
    private final GenreService genreService;

    @PostMapping("/add")
    public ResponseEntity<ApiResponseDTO<String>> addNewGenre(@Validated @RequestBody NewGenreDTO newGenreDTO) {
        return genreService.addNewGenre(newGenreDTO.type());
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponseDTO<PaginatedResponseDTO<GenrePreviewDTO>>> getAllGenres(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "24") int size, @RequestParam(required = false) String key) {
        return genreService.getAllGenres(page, size, key);
    }
}
