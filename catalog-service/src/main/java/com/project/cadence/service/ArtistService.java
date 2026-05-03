package com.project.cadence.service;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.PaginatedResponseDTO;
import com.project.cadence.dto.artist.*;
import com.project.cadence.dto.record.RecordPreviewDTO;
import com.project.cadence.dto.song.TopSongsInArtistProfileDTO;
import com.project.cadence.dto.user.UserPreviewDTO;
import com.project.cadence.model.*;
import com.project.cadence.repository.ArtistRepository;
import com.project.cadence.repository.RecordRepository;
import com.project.cadence.repository.SongRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistService {
    private final ArtistRepository artistRepository;
    private final SongRepository songRepository;
    private final RecordRepository recordRepository;
    private final AwsService awsService;
    private final JdbcTemplate jdbcTemplate;

    public ResponseEntity<ApiResponseDTO<String>> upsertArtist(UpsertArtistDTO dto) {
        try {
            Artist artist;
            if (dto.id().isPresent()) {
                artist = artistRepository.findById(dto.id().get())
                        .orElseThrow(() -> new RuntimeException("Artist not found"));

                artist.setName(dto.name());
                artist.setDescription(dto.description().orElse(null));

            } else {
                artist = Artist.builder()
                        .name(dto.name())
                        .description(dto.description().orElse(null))
                        .build();
            }

            Artist savedArtist = artistRepository.save(artist);
            return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseDTO<>(true, "Artist created successfully", savedArtist.getId()));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    @Transactional
    public ResponseEntity<ApiResponseDTO<Void>> deleteExistingArtist(String artistId) {
        try {
            Artist artist = artistRepository.findById(artistId).orElse(null);
            if (artist == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponseDTO<>(false, "Artist not found", null));
            }

            jdbcTemplate.update(
                    "DELETE FROM artist_created_songs WHERE artist_id = ?",
                    artistId
            );

            jdbcTemplate.update(
                    "DELETE FROM artist_records WHERE artist_id = ?",
                    artistId
            );

            jdbcTemplate.update(
                    "DELETE FROM artist_following WHERE artist_id = ?",
                    artistId
            );

            String coverUrl = artist.getProfileUrl();
            artistRepository.delete(artist);

            if (coverUrl != null) {
                String key = awsService.extractKeyFromUrl(coverUrl);
                if (awsService.findByName(key)) {
                    awsService.deleteObject(key);
                }
            }

            return ResponseEntity.ok(
                    new ApiResponseDTO<>(true, "Successfully deleted artist", null)
            );

        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>(false, "An error has occurred in the server", null));
        }
    }

    public ResponseEntity<ApiResponseDTO<PaginatedResponseDTO<ArtistPreviewDTO>>> getAllArtists(
            int page,
            int size,
            String key
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());

            Page<Artist> artistPage;
            if (key != null && !key.trim().isEmpty()) {
                artistPage = artistRepository.findByNameStartingWithIgnoreCase(key.trim(), pageable);
            } else {
                artistPage = artistRepository.findAll(pageable);
            }

            List<ArtistPreviewDTO> artistPreviewDTOS = artistPage
                    .stream()
                    .map(artist -> new ArtistPreviewDTO(
                            artist.getId(),
                            artist.getName(),
                            artist.getProfileUrl()
                    ))
                    .toList();

            PaginatedResponseDTO<ArtistPreviewDTO> response =
                    new PaginatedResponseDTO<>(
                            artistPreviewDTOS,
                            artistPage.getNumber(),
                            artistPage.getSize(),
                            artistPage.getTotalElements(),
                            artistPage.getTotalPages(),
                            artistPage.isLast()
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>(false, "An error has occurred in the server", null));
        }
    }

    public List<ArtistPreviewDTO> getArtistsForSearch(Pageable pageable, String key) {
        try {
            String searchKey = (key == null) ? "" : key.trim();
            Page<Artist> artistPage = artistRepository.findByNameContainingIgnoreCase(searchKey, pageable);
            return artistPage
                    .stream()
                    .map(artist -> new ArtistPreviewDTO(
                            artist.getId(),
                            artist.getName(),
                            artist.getProfileUrl()
                    ))
                    .toList();
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return List.of();
        }
    }

    public ResponseEntity<ApiResponseDTO<ArtistProfileDTO>> getArtistProfile(String artistId) {
        try {
            Artist artist = artistRepository.findById(artistId).orElse(null);
            if (artist == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponseDTO<>(false, "Artist not found", null));
            }

            // Fetch 5 latest records
            Set<RecordPreviewDTO> recordPreviewDTOS = new HashSet<>();
            recordRepository.findByArtistsOrderByReleaseTimestampDesc(artist, PageRequest.of(0, 5)).forEach(record -> recordPreviewDTOS.add(new RecordPreviewDTO(
                    record.getId(),
                    record.getTitle(),
                    record.getReleaseTimestamp(),
                    record.getCoverUrl(),
                    record.getRecordType(),
                    record.getArtists().stream()
                            .map(artist1 -> new ArtistPreviewDTO(
                                    artist1.getId(),
                                    artist1.getName(),
                                    artist1.getProfileUrl()
                            )).toList()
            )));

            // Fetch 10 popular songs
            Page<TopSongsInArtistProfileDTO> popularSongsPage = songRepository.findTopSongsForArtist(artistId, PageRequest.of(0, 10));

            List<TopSongsInArtistProfileDTO> popularSongs = popularSongsPage.stream()
                    .toList();

            for (TopSongsInArtistProfileDTO song : popularSongs) {
                List<Artist> createdBy = songRepository.findCreatorsBySongId(song.id());

                List<ArtistPreviewDTO> artistPreviewDTOS = createdBy.stream()
                        .map(a -> new ArtistPreviewDTO(
                                a.getId(),
                                a.getName(),
                                a.getProfileUrl()
                        ))
                        .toList();

                song.artists().addAll(artistPreviewDTOS);
            }

            int followerCount = getFollowerCount(artistId);

            return ResponseEntity.status(HttpStatus.OK).body(
                    new ApiResponseDTO<>(
                            true,
                            "Successfully retrieved the artist profile",
                            new ArtistProfileDTO(
                                    artist.getId(),
                                    artist.getName(),
                                    artist.getProfileUrl(),
                                    artist.getDescription(),
                                    followerCount,
                                    0L,
                                    popularSongs,
                                    recordPreviewDTOS
                            )
                    )
            );
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    @Transactional
    public ResponseEntity<ApiResponseDTO<Void>> followArtist(String artistId, String userId) {
        try {
            if (!userExists(userId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponseDTO<>(false, "An error occurred: requesting user not found", null));
            }

            if (!artistRepository.existsById(artistId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponseDTO<>(false, "Artist not found", null));
            }

            if (isUserFollowingArtist(userId, artistId)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponseDTO<>(false, "You are already following this artist", null));
            }

            Integer nextFollowOrder = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(follow_order) + 1, 0) FROM artist_following WHERE user_id = ?",
                    Integer.class,
                    userId
            );

            jdbcTemplate.update(
                    "INSERT INTO artist_following (user_id, artist_id, follow_order) VALUES (?, ?, ?)",
                    userId,
                    artistId,
                    nextFollowOrder == null ? 0 : nextFollowOrder
            );

            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseDTO<>(true, "Successfully followed the artist", null));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    @Transactional
    public ResponseEntity<ApiResponseDTO<Void>> unfollowArtist(String artistId, String userId) {
        try {
            if (!userExists(userId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponseDTO<>(false, "An error occurred: requesting user not found", null));
            }

            if (!artistRepository.existsById(artistId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponseDTO<>(false, "Artist not found", null));
            }

            if (!isUserFollowingArtist(userId, artistId)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponseDTO<>(false, "You are not following this artist", null));
            }

            jdbcTemplate.update(
                    "DELETE FROM artist_following WHERE user_id = ? AND artist_id = ?",
                    userId,
                    artistId
            );

            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseDTO<>(true, "Successfully unfollowed the artist", null));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    public ResponseEntity<ApiResponseDTO<Boolean>> isFollowing(String artistId, String userId) {
        try {
            if (!userExists(userId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponseDTO<>(false, "An error occurred: requesting user not found", null));
            }

            if (!artistRepository.existsById(artistId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponseDTO<>(false, "Artist not found", null));
            }

            if (isUserFollowingArtist(userId, artistId)) {
                return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseDTO<>(true, "You are following this artist", true));
            }

            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseDTO<>(true, "You are not following this artist", false));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponseDTO<>(false, "An error has occurred in the server", null));
        }
    }

    public ResponseEntity<ApiResponseDTO<List<UserPreviewDTO>>> getArtistFollowers(String artistId) {
        try {
            if (!artistRepository.existsById(artistId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponseDTO<>(false, "Artist not found", null));
            }

            List<UserPreviewDTO> followers = jdbcTemplate.query(
                    """
                            SELECT u.id, u.name, u.profile_url
                            FROM users u
                            JOIN artist_following af ON af.user_id = u.id
                            WHERE af.artist_id = ?
                            ORDER BY af.follow_order ASC
                            """,
                    (rs, rowNum) -> new UserPreviewDTO(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("profile_url")
                    ),
                    artistId
            );

            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseDTO<>(true, "Successfully retrieved artist followers", followers));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponseDTO<>(false, "An error has occurred in the server", null));
        }
    }

    private boolean userExists(String userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class,
                userId
        );

        return count != null && count > 0;
    }

    private boolean isUserFollowingArtist(String userId, String artistId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM artist_following WHERE user_id = ? AND artist_id = ?",
                Integer.class,
                userId,
                artistId
        );

        return count != null && count > 0;
    }

    private int getFollowerCount(String artistId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM artist_following WHERE artist_id = ?",
                Integer.class,
                artistId
        );

        return count == null ? 0 : count;
    }
}
