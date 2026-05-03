package com.project.cadence.controller;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.song.*;
import com.project.cadence.service.SongService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/song")
public class SongController {
    private final SongService songService;

    @GetMapping("/all")
    public ResponseEntity<ApiResponseDTO<List<EachSongDTO>>> getAllSongsByRecordId(
            @RequestParam String recordId
    ) {
        return songService.getAllSongsByRecordId(recordId);
    }

    @GetMapping("/{songId}")
    public ResponseEntity<ApiResponseDTO<EachSongDTO>> getSongById(
            @PathVariable("songId") String songId
    ) {
        return songService.getSongById(songId);
    }
}
