package com.project.cadence.integration;

import com.amazonaws.HttpMethod;
import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.record.UpsertRecordDTO;
import com.project.cadence.dto.record.UpsertRecordResponseDTO;
import com.project.cadence.dto.song.UpsertSongDTO;
import com.project.cadence.model.Artist;
import com.project.cadence.model.Genre;
import com.project.cadence.model.Record;
import com.project.cadence.model.RecordType;
import com.project.cadence.repository.ArtistRepository;
import com.project.cadence.repository.GenreRepository;
import com.project.cadence.repository.RecordRepository;
import com.project.cadence.service.AwsService;
import com.project.cadence.service.RecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordServiceIT extends BaseIntegrationTest {

    @Autowired RecordService recordService;
    @Autowired ArtistRepository artistRepository;
    @Autowired GenreRepository genreRepository;
    @Autowired RecordRepository recordRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockBean AwsService awsService;

    private Artist drake;
    private Artist kendrick;
    private Genre hipHop;
    private Genre rb;

    @BeforeEach
    void setUp() {
        // Brute-force reset across cascaded join tables — deleteAll() can leave
        // orphaned join-table rows that cause unique-constraint violations on re-insert.
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of(
                "artist_following", "users",
                "artist_created_songs", "artist_records", "song_genre",
                "song", "record", "artist", "genre"
        )) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        drake = artistRepository.save(Artist.builder().name("Drake").build());
        kendrick = artistRepository.save(Artist.builder().name("Kendrick").build());
        hipHop = genreRepository.save(Genre.builder().type("HIP_HOP").build());
        rb = genreRepository.save(Genre.builder().type("R_B").build());

        when(awsService.getUrl(anyString())).thenAnswer(inv -> "https://cdn.test/" + inv.getArgument(0));
        when(awsService.getPresignedUrl(anyString(), anyString(), anyString(), any(HttpMethod.class)))
                .thenAnswer(inv -> "https://signed.test/" + inv.getArgument(2));
    }

    @Test
    @Transactional
    void upsertNewRecord_persistsRecord_withSongsAndArtists_andPublishesEvent() {
        UpsertSongDTO song1 = new UpsertSongDTO(
                Optional.empty(), "God's Plan", Set.of(hipHop.getId()), List.of(drake.getId()), 198
        );
        UpsertSongDTO song2 = new UpsertSongDTO(
                Optional.empty(), "Nice For What", Set.of(hipHop.getId(), rb.getId()), List.of(drake.getId()), 210
        );
        UpsertRecordDTO dto = new UpsertRecordDTO(
                Optional.empty(), "Scorpion", 1530000000000L, RecordType.ALBUM,
                List.of(drake.getId()), List.of(song1, song2)
        );

        ResponseEntity<ApiResponseDTO<UpsertRecordResponseDTO>> result = recordService.upsertNewRecord(dto);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UpsertRecordResponseDTO body = result.getBody().data();
        assertThat(body).isNotNull();
        assertThat(body.id()).isNotBlank();
        assertThat(body.songs()).hasSize(2);
        assertThat(body.songs()).allSatisfy(s -> assertThat(s.presignedUrl()).startsWith("https://signed.test/"));

        Record reloaded = recordRepository.findById(body.id()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Scorpion");
        assertThat(reloaded.getRecordType()).isEqualTo(RecordType.ALBUM);
        assertThat(reloaded.getArtists()).extracting(Artist::getName).containsExactly("Drake");
        assertThat(reloaded.getSongs()).hasSize(2);
        assertThat(reloaded.getSongs()).extracting("title")
                .containsExactlyInAnyOrder("God's Plan", "Nice For What");
    }

    @Test
    void upsertNewRecord_returns400_whenAnyArtistIdIsUnknown() {
        UpsertSongDTO song = new UpsertSongDTO(
                Optional.empty(), "Title", Set.of(hipHop.getId()), List.of(drake.getId()), 100
        );
        UpsertRecordDTO dto = new UpsertRecordDTO(
                Optional.empty(), "Phantom", 1L, RecordType.ALBUM,
                List.of(drake.getId(), "ghost-artist"), List.of(song)
        );

        ResponseEntity<ApiResponseDTO<UpsertRecordResponseDTO>> result = recordService.upsertNewRecord(dto);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody().message()).contains("artists not found");
        assertThat(recordRepository.count()).isZero();
    }

    @Test
    void upsertNewRecord_returns400_whenSongGenreIdIsUnknown() {
        UpsertSongDTO song = new UpsertSongDTO(
                Optional.empty(), "Title", Set.of("ghost-genre"), List.of(drake.getId()), 100
        );
        UpsertRecordDTO dto = new UpsertRecordDTO(
                Optional.empty(), "Phantom", 1L, RecordType.ALBUM,
                List.of(drake.getId()), List.of(song)
        );

        ResponseEntity<ApiResponseDTO<UpsertRecordResponseDTO>> result = recordService.upsertNewRecord(dto);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody().message()).isEqualTo("Invalid Genres");
        assertThat(recordRepository.count()).isZero();
    }

    @Test
    void upsertNewRecord_returns400_whenSongArtistIdIsUnknown() {
        UpsertSongDTO song = new UpsertSongDTO(
                Optional.empty(), "Title", Set.of(hipHop.getId()), List.of("ghost-artist"), 100
        );
        UpsertRecordDTO dto = new UpsertRecordDTO(
                Optional.empty(), "Phantom", 1L, RecordType.ALBUM,
                List.of(drake.getId()), List.of(song)
        );

        ResponseEntity<ApiResponseDTO<UpsertRecordResponseDTO>> result = recordService.upsertNewRecord(dto);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody().message()).isEqualTo("Invalid Artists");
    }

    @Test
    @Transactional
    void upsertNewRecord_updateExisting_replacesTitleAndSongs() {
        UpsertRecordDTO create = new UpsertRecordDTO(
                Optional.empty(), "Original Title", 1L, RecordType.ALBUM,
                List.of(drake.getId()),
                List.of(new UpsertSongDTO(Optional.empty(), "Old Song", Set.of(hipHop.getId()), List.of(drake.getId()), 100))
        );
        String recordId = recordService.upsertNewRecord(create).getBody().data().id();

        UpsertRecordDTO update = new UpsertRecordDTO(
                Optional.of(recordId), "Updated Title", 2L, RecordType.EP,
                List.of(drake.getId(), kendrick.getId()),
                List.of(
                        new UpsertSongDTO(Optional.empty(), "New Song A", Set.of(rb.getId()), List.of(kendrick.getId()), 150),
                        new UpsertSongDTO(Optional.empty(), "New Song B", Set.of(rb.getId()), List.of(kendrick.getId()), 160)
                )
        );

        ResponseEntity<ApiResponseDTO<UpsertRecordResponseDTO>> result = recordService.upsertNewRecord(update);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Record reloaded = recordRepository.findById(recordId).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Updated Title");
        assertThat(reloaded.getRecordType()).isEqualTo(RecordType.EP);
        assertThat(reloaded.getArtists()).extracting(Artist::getName)
                .containsExactlyInAnyOrder("Drake", "Kendrick");
        assertThat(reloaded.getSongs()).extracting("title")
                .containsExactlyInAnyOrder("New Song A", "New Song B");
    }

    @Test
    @Transactional
    void deleteRecord_removesRecord_andDeletesS3Objects() {
        UpsertRecordDTO create = new UpsertRecordDTO(
                Optional.empty(), "Disposable", 1L, RecordType.SINGLE,
                List.of(drake.getId()),
                List.of(new UpsertSongDTO(Optional.empty(), "OneSong", Set.of(hipHop.getId()), List.of(drake.getId()), 100))
        );
        String recordId = recordService.upsertNewRecord(create).getBody().data().id();

        Record record = recordRepository.findById(recordId).orElseThrow();
        record.getSongs().forEach(s -> s.setSongUrl("https://cdn.test/song/song_url/" + s.getId()));
        record.setCoverUrl("https://cdn.test/record/cover/" + recordId);
        recordRepository.save(record);

        when(awsService.extractKeyFromUrl(anyString())).thenAnswer(inv -> {
            String url = inv.getArgument(0);
            return url.substring(url.indexOf(".test/") + 6);
        });

        ResponseEntity<ApiResponseDTO<Void>> result = recordService.deleteRecord(recordId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recordRepository.findById(recordId)).isEmpty();
        verify(awsService, times(1)).deleteObject("record/cover/" + recordId);
        verify(awsService, times(1)).deleteObject(org.mockito.ArgumentMatchers.startsWith("song/song_url/"));
    }

    @Test
    void deleteRecord_returns404_whenRecordNotFound() {
        ResponseEntity<ApiResponseDTO<Void>> result = recordService.deleteRecord("ghost-record");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody().message()).isEqualTo("Record not found");
        verify(awsService, never()).deleteObject(anyString());
    }

    @Test
    void getRecordById_returns200_forExisting_and404_forMissing() {
        UpsertRecordDTO dto = new UpsertRecordDTO(
                Optional.empty(), "Findable", 100L, RecordType.ALBUM,
                List.of(drake.getId()),
                List.of(new UpsertSongDTO(Optional.empty(), "S", Set.of(hipHop.getId()), List.of(drake.getId()), 50))
        );
        String id = recordService.upsertNewRecord(dto).getBody().data().id();

        var ok = recordService.getRecordById(id);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().data().title()).isEqualTo("Findable");

        var missing = recordService.getRecordById("ghost-id");
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getAllRecordsByArtistId_returnsRecords_forKnownArtist_andOrders_byReleaseDesc() {
        UpsertRecordDTO older = new UpsertRecordDTO(
                Optional.empty(), "Older", 100L, RecordType.ALBUM, List.of(drake.getId()),
                List.of(new UpsertSongDTO(Optional.empty(), "S1", Set.of(hipHop.getId()), List.of(drake.getId()), 50)));
        UpsertRecordDTO newer = new UpsertRecordDTO(
                Optional.empty(), "Newer", 200L, RecordType.ALBUM, List.of(drake.getId()),
                List.of(new UpsertSongDTO(Optional.empty(), "S2", Set.of(hipHop.getId()), List.of(drake.getId()), 50)));
        recordService.upsertNewRecord(older);
        recordService.upsertNewRecord(newer);

        var result = recordService.getAllRecordsByArtistId(drake.getId());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).extracting("title").containsExactly("Newer", "Older");
    }

    @Test
    void getAllRecordsByArtistId_returns404_forUnknownArtist() {
        var result = recordService.getAllRecordsByArtistId("ghost-artist");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody().message()).isEqualTo("Artist not found");
    }
}
