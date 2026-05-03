package com.project.cadence.service;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.record.RecordPreviewDTO;
import com.project.cadence.model.Artist;
import com.project.cadence.model.Record;
import com.project.cadence.model.RecordType;
import com.project.cadence.producers.RecordCreatedProducer;
import com.project.cadence.repository.ArtistRepository;
import com.project.cadence.repository.GenreRepository;
import com.project.cadence.repository.RecordRepository;
import com.project.cadence.repository.SongRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordServiceTest {

    @Mock private RecordRepository recordRepository;
    @Mock private ArtistRepository artistRepository;
    @Mock private GenreRepository genreRepository;
    @Mock private SongRepository songRepository;
    @Mock private AwsService awsService;
    @Mock private RecordCreatedProducer producer;
    @Mock private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @InjectMocks
    private RecordService recordService;

    private static final String RECORD_ID = "record-1";
    private static final String ARTIST_ID = "artist-1";

    private Artist artist() {
        return Artist.builder().id(ARTIST_ID).name("Drake").build();
    }

    private Record record() {
        return Record.builder()
                .id(RECORD_ID)
                .title("Certified Lover Boy")
                .releaseTimestamp(1000L)
                .recordType(RecordType.ALBUM)
                .build();
    }

    // ── deleteRecord ──────────────────────────────────────────────────────────

    @Test
    void deleteRecord_returns404_whenRecordNotFound() {
        when(recordRepository.findById(RECORD_ID)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponseDTO<Void>> response = recordService.deleteRecord(RECORD_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().success()).isFalse();
    }

    @Test
    void deleteRecord_returns200_andDeletesRecord_whenFound() {
        Record r = record();
        when(recordRepository.findById(RECORD_ID)).thenReturn(Optional.of(r));

        ResponseEntity<ApiResponseDTO<Void>> response = recordService.deleteRecord(RECORD_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
        verify(recordRepository).delete(r);
    }

    // ── getAllRecordsByArtistId ────────────────────────────────────────────────

    @Test
    void getAllRecordsByArtistId_returns404_whenArtistNotFound() {
        when(artistRepository.findById(ARTIST_ID)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponseDTO<List<RecordPreviewDTO>>> response =
                recordService.getAllRecordsByArtistId(ARTIST_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getAllRecordsByArtistId_returns200_withRecordList_whenArtistFound() {
        Artist a = artist();
        Record r = record();
        when(artistRepository.findById(ARTIST_ID)).thenReturn(Optional.of(a));
        when(recordRepository.findByArtistsOrderByReleaseTimestampDesc(any(Artist.class), any(Pageable.class)))
                .thenReturn(List.of(r));

        ResponseEntity<ApiResponseDTO<List<RecordPreviewDTO>>> response =
                recordService.getAllRecordsByArtistId(ARTIST_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).hasSize(1);
        assertThat(response.getBody().data().get(0).title()).isEqualTo("Certified Lover Boy");
    }
}
