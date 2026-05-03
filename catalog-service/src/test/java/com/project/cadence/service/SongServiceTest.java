package com.project.cadence.service;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.song.EachSongDTO;
import com.project.cadence.model.Artist;
import com.project.cadence.model.Genre;
import com.project.cadence.model.Record;
import com.project.cadence.model.RecordType;
import com.project.cadence.model.Song;
import com.project.cadence.repository.RecordRepository;
import com.project.cadence.repository.SongRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SongServiceTest {

    @Mock private RecordRepository recordRepository;
    @Mock private SongRepository songRepository;

    @InjectMocks
    private SongService songService;

    private static final String RECORD_ID = "record-1";
    private static final String SONG_ID = "song-1";

    private Song song() {
        Artist artist = Artist.builder().id("a1").name("Drake").build();
        Genre genre = Genre.builder().id("g1").type("HIP_HOP").build();
        Record record = Record.builder()
                .id(RECORD_ID)
                .title("Certified Lover Boy")
                .releaseTimestamp(1000L)
                .recordType(RecordType.ALBUM)
                .build();
        return Song.builder()
                .id(SONG_ID)
                .title("Gods Plan")
                .totalDuration(198)
                .createdBy(List.of(artist))
                .genres(Set.of(genre))
                .record(record)
                .build();
    }

    // ── getSongById ───────────────────────────────────────────────────────────

    @Test
    void getSongById_returns200_withSongDetails_whenFound() {
        Song s = song();
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(s));

        ResponseEntity<ApiResponseDTO<EachSongDTO>> response = songService.getSongById(SONG_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        EachSongDTO dto = response.getBody().data();
        assertThat(dto.id()).isEqualTo(SONG_ID);
        assertThat(dto.title()).isEqualTo("Gods Plan");
        assertThat(dto.artists()).hasSize(1);
        assertThat(dto.artists().get(0).name()).isEqualTo("Drake");
    }

    @Test
    void getSongById_returns404_whenNotFound() {
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponseDTO<EachSongDTO>> response = songService.getSongById(SONG_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().success()).isFalse();
    }

    // ── getAllSongsByRecordId ──────────────────────────────────────────────────

    @Test
    void getAllSongsByRecordId_returns200_withSongList_whenRecordExists() {
        Song s = song();
        when(recordRepository.existsById(RECORD_ID)).thenReturn(true);
        when(recordRepository.getAllSongsByRecordId(RECORD_ID)).thenReturn(List.of(s));

        ResponseEntity<ApiResponseDTO<List<EachSongDTO>>> response =
                songService.getAllSongsByRecordId(RECORD_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).hasSize(1);
        assertThat(response.getBody().data().get(0).title()).isEqualTo("Gods Plan");
    }

    @Test
    void getAllSongsByRecordId_returns400_whenRecordNotFound() {
        when(recordRepository.existsById(RECORD_ID)).thenReturn(false);

        ResponseEntity<ApiResponseDTO<List<EachSongDTO>>> response =
                songService.getAllSongsByRecordId(RECORD_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().success()).isFalse();
    }
}
