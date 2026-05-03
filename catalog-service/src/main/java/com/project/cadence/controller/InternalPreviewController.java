package com.project.cadence.controller;

import com.project.cadence.dto.artist.ArtistPreviewDTO;
import com.project.cadence.dto.genre.GenrePreviewDTO;
import com.project.cadence.dto.internal.SongPreviewRequestDTO;
import com.project.cadence.dto.internal.SongStreamingMetadataDTO;
import com.project.cadence.dto.record.RecordPreviewWithCoverImageDTO;
import com.project.cadence.dto.song.EachSongDTO;
import com.project.cadence.model.Song;
import com.project.cadence.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal")
public class InternalPreviewController {
    private final SongRepository songRepository;

    @PostMapping("/songs/preview")
    public ResponseEntity<List<EachSongDTO>> getSongPreviews(@RequestBody SongPreviewRequestDTO request) {
        List<String> songIds = request.songIds() == null ? List.of() : request.songIds();

        Map<String, Song> songsById = StreamSupport.stream(songRepository.findAllById(songIds).spliterator(), false)
                .collect(Collectors.toMap(Song::getId, Function.identity()));

        List<EachSongDTO> songs = songIds.stream()
                .map(songsById::get)
                .filter(song -> song != null)
                .map(song -> new EachSongDTO(
                        song.getId(),
                        song.getTitle(),
                        song.getTotalDuration(),
                        song.getCreatedBy().stream()
                                .map(artist -> new ArtistPreviewDTO(
                                        artist.getId(),
                                        artist.getName(),
                                        artist.getProfileUrl()
                                ))
                                .toList(),
                        song.getGenres().stream()
                                .map(genre -> new GenrePreviewDTO(
                                        genre.getId(),
                                        genre.getType()
                                ))
                                .toList(),
                        new RecordPreviewWithCoverImageDTO(
                                song.getRecord().getId(),
                                song.getRecord().getTitle(),
                                song.getRecord().getCoverUrl(),
                                song.getRecord().getReleaseTimestamp()
                        )
                ))
                .toList();

        return ResponseEntity.ok(songs);
    }

    @GetMapping("/songs/{songId}/streaming-metadata")
    public ResponseEntity<SongStreamingMetadataDTO> getSongStreamingMetadata(@PathVariable String songId) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new RuntimeException("Song not found"));

        return ResponseEntity.ok(new SongStreamingMetadataDTO(
                song.getId(),
                song.getSongUrl()
        ));
    }
}
