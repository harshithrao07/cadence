package com.project.cadence.service;

import com.amazonaws.HttpMethod;
import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.artist.ArtistPreviewDTO;
import com.project.cadence.dto.record.UpsertRecordDTO;
import com.project.cadence.dto.record.RecordPreviewDTO;
import com.project.cadence.dto.record.UpsertRecordResponseDTO;
import com.project.cadence.dto.song.SongResponseDTO;
import com.project.cadence.dto.song.UpsertSongDTO;
import com.project.cadence.events.RecordCreatedEvent;
import com.project.cadence.model.*;
import com.project.cadence.model.Record;
import com.project.cadence.producers.RecordCreatedProducer;
import com.project.cadence.repository.ArtistRepository;
import com.project.cadence.repository.GenreRepository;
import com.project.cadence.repository.RecordRepository;
import com.project.cadence.repository.SongRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordService {
    private final RecordRepository recordRepository;
    private final ArtistRepository artistRepository;
    private final GenreRepository genreRepository;
    private final SongRepository songRepository;
    private final AwsService awsService;
    private final RecordCreatedProducer producer;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Transactional
    public ResponseEntity<ApiResponseDTO<UpsertRecordResponseDTO>> upsertNewRecord(UpsertRecordDTO upsertRecordDTO) {
        try {
            List<Artist> recordArtists = artistRepository.findAllById(upsertRecordDTO.artistIds());
            if (recordArtists.size() != upsertRecordDTO.artistIds().size()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponseDTO<>(false, "One or more artists not found", null));
            }

            Record record;
            if (upsertRecordDTO.id().isPresent()) {
                record = recordRepository.findById(upsertRecordDTO.id().get())
                        .orElseThrow(() -> new RuntimeException("Record not found"));
            } else {
                record = new Record();
            }

            record.setTitle(upsertRecordDTO.title());
            record.setReleaseTimestamp(upsertRecordDTO.releaseTimestamp());
            record.setRecordType(upsertRecordDTO.recordType());
            record.setArtists(recordArtists);

            // Adding the songs part
            Set<String> allGenreIds = upsertRecordDTO.songs().stream()
                    .map(UpsertSongDTO::genreIds)
                    .flatMap(Set::stream)
                    .collect(Collectors.toSet());

            Set<String> allArtistIds = upsertRecordDTO.songs().stream()
                    .map(UpsertSongDTO::artistIds)
                    .flatMap(List::stream)
                    .collect(Collectors.toSet());

            List<Genre> genres = genreRepository.findAllById(allGenreIds);
            if (genres.size() != allGenreIds.size()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponseDTO<>(false, "Invalid Genres", null));
            }

            List<Artist> songArtists = artistRepository.findAllById(allArtistIds);
            if (songArtists.size() != allArtistIds.size()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponseDTO<>(false, "Invalid Artists", null));
            }

            Map<String, Genre> genreMap = genres.stream()
                    .collect(Collectors.toMap(Genre::getId, Function.identity()));

            Map<String, Artist> artistMap = songArtists.stream()
                    .collect(Collectors.toMap(Artist::getId, Function.identity()));

            List<Song> songs = upsertRecordDTO.songs().stream()
                    .map(eachNewSongDTO -> {
                        Song song;
                        if (eachNewSongDTO.id().isPresent()) {
                            song = songRepository.findById(eachNewSongDTO.id().get())
                                    .orElseThrow(() -> new RuntimeException("Song not found"));
                        } else {
                            song = new Song();
                        }

                        song.setTitle(eachNewSongDTO.title());
                        song.setRecord(record);
                        song.setGenres(eachNewSongDTO.genreIds().stream()
                                .map(genreMap::get)
                                .collect(Collectors.toSet())
                        );
                        song.setCreatedBy(eachNewSongDTO.artistIds().stream()
                                .map(artistMap::get)
                                .collect(Collectors.toList())
                        );
                        song.setTotalDuration(eachNewSongDTO.totalDuration());

                        return song;
                    })
                    .collect(Collectors.toList());

            record.getSongs().clear();
            record.getSongs().addAll(songs);
            Record savedRecord = recordRepository.save(record);

            List<String> artistNames = savedRecord.getArtists().stream()
                    .map(Artist::getName)
                    .toList();
            List<String> artistIds = savedRecord.getArtists().stream()
                    .map(Artist::getId)
                    .toList();
            List<String> followerEmails = getFollowerEmails(artistIds);

            RecordCreatedEvent recordCreatedEvent = new RecordCreatedEvent(
                    savedRecord.getId(),
                    savedRecord.getTitle(),
                    artistNames,
                    savedRecord.getCoverUrl(),
                    followerEmails
            );
            producer.send(recordCreatedEvent);

            savedRecord.getSongs().forEach(song -> {
                String objectKey = "song/song_url/" + song.getId();
                String url = awsService.getUrl(objectKey);
                song.setSongUrl(url);
            });

            UpsertRecordResponseDTO response = new UpsertRecordResponseDTO(
                    savedRecord.getId(),
                    savedRecord.getSongs().stream()
                            .map(song -> new SongResponseDTO(
                                    song.getId(),
                                    song.getTitle(),
                                    awsService.getPresignedUrl("song", "song_url", song.getId(), HttpMethod.PUT)
                            ))
                            .toList()
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponseDTO<>(true, "Upsert successful", response));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    @Transactional
    public ResponseEntity<ApiResponseDTO<Void>> deleteRecord(String recordId) {
        try {
            Record record = recordRepository.findById(recordId).orElse(null);
            if (record == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponseDTO<>(false, "Record not found", null));
            }

            List<String> songKeys = record.getSongs().stream()
                    .map(Song::getSongUrl)
                    .filter(Objects::nonNull)
                    .map(awsService::extractKeyFromUrl)
                    .toList();

            String coverKey = record.getCoverUrl() != null
                    ? awsService.extractKeyFromUrl(record.getCoverUrl())
                    : null;

            recordRepository.delete(record);

            songKeys.forEach(awsService::deleteObject);
            if (coverKey != null) {
                awsService.deleteObject(coverKey);
            }

            return ResponseEntity.ok(
                    new ApiResponseDTO<>(true, "Successfully deleted the record", null)
            );
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    public ResponseEntity<ApiResponseDTO<List<RecordPreviewDTO>>> getAllRecordsByArtistId(String artistId) {
        try {
            Artist artist = artistRepository.findById(artistId).orElse(null);
            if (artist == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponseDTO<>(false, "Artist not found", null));
            }
            List<Record> records = recordRepository.findByArtistsOrderByReleaseTimestampDesc(artist, PageRequest.of(0, 5));

            List<RecordPreviewDTO> recordPreviewDTOS = new ArrayList<>();
            records.forEach(record -> {
                List<ArtistPreviewDTO> artistPreviewDTOS = new ArrayList<>();
                for (Artist artist1 : record.getArtists()) {
                    artistPreviewDTOS.add(new ArtistPreviewDTO(
                            artist1.getId(),
                            artist1.getName(),
                            artist1.getProfileUrl()
                    ));
                }

                recordPreviewDTOS.add(new RecordPreviewDTO(
                        record.getId(),
                        record.getTitle(),
                        record.getReleaseTimestamp(),
                        record.getCoverUrl(),
                        record.getRecordType(),
                        artistPreviewDTOS
                ));
            });

            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseDTO<>(true, "Successfully retrieved records", recordPreviewDTOS));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    public ResponseEntity<ApiResponseDTO<RecordPreviewDTO>> getRecordById(String recordId) {
        try {
            Record record = recordRepository.findById(recordId).orElse(null);

            if (record == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponseDTO<>(false, "Record not found", null));
            }

            List<ArtistPreviewDTO> artistPreviewDTOS = new ArrayList<>();
            for (Artist artist : record.getArtists()) {
                artistPreviewDTOS.add(new ArtistPreviewDTO(
                        artist.getId(),
                        artist.getName(),
                        artist.getProfileUrl()
                ));
            }

            RecordPreviewDTO recordPreviewDTO = new RecordPreviewDTO(
                    record.getId(),
                    record.getTitle(),
                    record.getReleaseTimestamp(),
                    record.getCoverUrl(),
                    record.getRecordType(),
                    artistPreviewDTOS
            );

            return ResponseEntity.ok(
                    new ApiResponseDTO<>(true, "Successfully retrieved record " + recordId, recordPreviewDTO)
            );

        } catch (Exception e) {
            log.error("An exception has occurred", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    public List<RecordPreviewDTO> getRecordsForSearch(Pageable pageable, String key) {
        try {
            String searchKey = (key == null) ? "" : key.trim();
            Page<Record> recordPage =
                    recordRepository.findByTitleContainingIgnoreCase(
                            searchKey,
                            pageable
                    );

            return recordPage.getContent()
                    .stream()
                    .map(record -> new RecordPreviewDTO(
                                    record.getId(),
                                    record.getTitle(),
                                    record.getReleaseTimestamp(),
                                    record.getCoverUrl(),
                                    record.getRecordType(),
                                    record.getArtists().stream()
                                            .map(artist -> new ArtistPreviewDTO(
                                                    artist.getId(),
                                                    artist.getName(),
                                                    artist.getProfileUrl()
                                            )).toList()
                            )
                    )
                    .toList();
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return List.of();
        }
    }

    private List<String> getFollowerEmails(List<String> artistIds) {
        if (artistIds.isEmpty()) {
            return List.of();
        }

        return namedParameterJdbcTemplate.queryForList(
                """
                        SELECT DISTINCT u.email
                        FROM artist_following af
                        JOIN users u ON u.id = af.user_id
                        WHERE af.artist_id IN (:artistIds)
                        """,
                new MapSqlParameterSource("artistIds", artistIds),
                String.class
        );
    }

}
