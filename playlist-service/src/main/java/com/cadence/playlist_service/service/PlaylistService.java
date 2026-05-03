package com.cadence.playlist_service.service;

import com.cadence.playlist_service.client.CatalogPreviewClient;
import com.cadence.playlist_service.client.UserPreviewClient;
import com.cadence.playlist_service.dto.ApiResponseDTO;
import com.cadence.playlist_service.dto.EachSongDTO;
import com.cadence.playlist_service.dto.PlaylistPreviewDTO;
import com.cadence.playlist_service.dto.UpsertPlaylistDTO;
import com.cadence.playlist_service.model.LikedPlaylist;
import com.cadence.playlist_service.model.LikedPlaylistId;
import com.cadence.playlist_service.model.Playlist;
import com.cadence.playlist_service.model.PlaylistVisibility;
import com.cadence.playlist_service.model.SystemPlaylistType;
import com.cadence.playlist_service.repository.LikedPlaylistRepository;
import com.cadence.playlist_service.repository.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaylistService {
    private final PlaylistRepository playlistRepository;
    private final LikedPlaylistRepository likedPlaylistRepository;
    private final UserPreviewClient userPreviewClient;
    private final CatalogPreviewClient catalogPreviewClient;

    public ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> getAllPlaylists(String userId) {
        try {
            List<PlaylistPreviewDTO> playlists = playlistRepository.findAllByOwnerIdAndIsSystemFalseOrderByCreatedAtDesc(userId).stream()
                    .map(this::toPreview)
                    .toList();

            return ResponseEntity.ok(new ApiResponseDTO<>(true, "Playlists fetched successfully", playlists));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    public ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> getCreatedPlaylistsForProfile(
            String userId,
            boolean includePrivate
    ) {
        try {
            List<PlaylistPreviewDTO> playlists = playlistRepository.findAllByOwnerIdAndIsSystemFalseOrderByCreatedAtDesc(userId)
                    .stream()
                    .filter(playlist -> includePrivate || playlist.getVisibility() != PlaylistVisibility.PRIVATE)
                    .map(this::toPreview)
                    .toList();

            return ResponseEntity.ok(new ApiResponseDTO<>(true, "Created playlists fetched successfully", playlists));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    public ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> getLikedPlaylistsForProfile(String userId) {
        try {
            List<String> playlistIds = likedPlaylistRepository.findByIdUserIdOrderByLikeOrderAsc(userId)
                    .stream()
                    .map(likedPlaylist -> likedPlaylist.getId().getPlaylistId())
                    .toList();

            List<Playlist> playlists = playlistRepository.findAllById(playlistIds);
            List<PlaylistPreviewDTO> playlistPreviews = playlistIds.stream()
                    .flatMap(playlistId -> playlists.stream()
                            .filter(playlist -> playlist.getId().equals(playlistId))
                            .filter(playlist -> playlist.getVisibility() != PlaylistVisibility.PRIVATE)
                            .map(this::toPreview)
                    )
                    .toList();

            return ResponseEntity.ok(new ApiResponseDTO<>(true, "Liked playlists fetched successfully", playlistPreviews));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    public ResponseEntity<ApiResponseDTO<String>> upsertPlaylist(String userId, UpsertPlaylistDTO dto) {
        try {
            Playlist playlist;

            if (dto.id().isPresent()) {
                playlist = playlistRepository.findByIdAndOwnerId(dto.id().get(), userId)
                        .orElseThrow(() -> new RuntimeException("Playlist not found or access denied"));

                if (playlist.isSystem()) {
                    throw new RuntimeException("You are not allowed to edit this playlist");
                }
            } else {
                playlist = new Playlist();
                playlist.setOwnerId(userId);
                playlist.setVisibility(PlaylistVisibility.PUBLIC);
            }

            playlist.setName(dto.name());
            dto.visibility().ifPresent(playlist::setVisibility);

            Playlist savedPlaylist = playlistRepository.save(playlist);
            return ResponseEntity.ok(new ApiResponseDTO<>(true, "Successfully upserted playlist", savedPlaylist.getId()));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    @Transactional
    public ResponseEntity<ApiResponseDTO<Void>> addSongToPlaylist(String userId, String playlistId, String songId) {
        try {
            Playlist playlist = getOwnedPlaylist(userId, playlistId);
            if (!catalogPreviewClient.songExists(songId)) {
                throw new RuntimeException("Song not found");
            }

            if (!playlist.getSongIds().contains(songId)) {
                playlist.getSongIds().add(songId);
                playlistRepository.save(playlist);
            }

            return ResponseEntity.ok(new ApiResponseDTO<>(true, "Successfully upserted playlist", null));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    @Transactional
    public ResponseEntity<ApiResponseDTO<Void>> removeSongFromPlaylist(String userId, String playlistId, String songId) {
        try {
            Playlist playlist = getOwnedPlaylist(userId, playlistId);
            boolean removed = playlist.getSongIds().remove(songId);

            if (!removed) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponseDTO<>(false, "Song not present in playlist", null));
            }

            playlistRepository.save(playlist);

            return ResponseEntity.ok(new ApiResponseDTO<>(true, "Song removed from playlist", null));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    public ResponseEntity<ApiResponseDTO<Void>> likePlaylist(String userId, String playlistId) {
        try {
            if (!playlistRepository.existsById(playlistId)) {
                throw new RuntimeException("Playlist not found");
            }

            LikedPlaylistId id = new LikedPlaylistId(userId, playlistId);
            if (!likedPlaylistRepository.existsById(id)) {
                likedPlaylistRepository.save(LikedPlaylist.builder()
                        .id(id)
                        .likeOrder((int) likedPlaylistRepository.countByIdUserId(userId))
                        .build());
            }

            return ResponseEntity.ok(new ApiResponseDTO<>(true, "Playlist liked successfully", null));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    public ResponseEntity<ApiResponseDTO<Void>> unlikePlaylist(String userId, String playlistId) {
        try {
            LikedPlaylistId id = new LikedPlaylistId(userId, playlistId);
            if (likedPlaylistRepository.existsById(id)) {
                likedPlaylistRepository.deleteById(id);
            }

            return ResponseEntity.ok(new ApiResponseDTO<>(true, "Playlist unliked successfully", null));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    @Transactional
    public void createLikedSongsPlaylistForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        String playlistId = SystemPlaylistType.LIKED_SONGS.name() + "_" + userId;
        if (playlistRepository.existsById(playlistId)) {
            return;
        }

        Playlist likedSongs = Playlist.builder()
                .name("Liked Songs")
                .ownerId(userId)
                .isSystem(true)
                .visibility(PlaylistVisibility.PRIVATE)
                .systemType(SystemPlaylistType.LIKED_SONGS)
                .build();

        playlistRepository.save(likedSongs);
    }

    public ResponseEntity<ApiResponseDTO<PlaylistPreviewDTO>> getPlaylist(String userId, String playlistId) {
        try {
            Playlist playlist = playlistRepository.findById(playlistId)
                    .orElseThrow(() -> new RuntimeException("Playlist not found"));

            boolean isOwner = playlist.getOwnerId().equals(userId);
            if (playlist.getVisibility() == PlaylistVisibility.PRIVATE && !isOwner) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponseDTO<>(false, "Access denied", null));
            }

            return ResponseEntity.ok(new ApiResponseDTO<>(true, "Playlist fetched successfully", toPreview(playlist)));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    public ResponseEntity<ApiResponseDTO<List<EachSongDTO>>> getSongsFromPlaylist(String userId, String playlistId) {
        try {
            Playlist playlist = playlistRepository.findById(playlistId)
                    .orElseThrow(() -> new RuntimeException("Playlist not found"));

            boolean isOwner = playlist.getOwnerId().equals(userId);
            if (playlist.getVisibility() == PlaylistVisibility.PRIVATE && !isOwner) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponseDTO<>(false, "Access denied", null));
            }

            List<String> orderedSongIds = playlist.getSongIds();

            if (playlist.isSystem() && playlist.getSystemType() == SystemPlaylistType.LIKED_SONGS) {
                orderedSongIds = new ArrayList<>(orderedSongIds);
                Collections.reverse(orderedSongIds);
            }

            List<EachSongDTO> songs = catalogPreviewClient.getSongPreviews(orderedSongIds);

            return ResponseEntity.ok(new ApiResponseDTO<>(true, "Playlist songs fetched successfully", songs));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    public ResponseEntity<ApiResponseDTO<Void>> deletePlaylist(String userId, String playlistId) {
        try {
            Playlist playlist = playlistRepository.findById(playlistId)
                    .orElseThrow(() -> new RuntimeException("Playlist not found"));

            if (!playlist.getOwnerId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponseDTO<>(false, "Access denied", null));
            }

            if (playlist.isSystem()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponseDTO<>(false, "System playlists cannot be deleted", null));
            }

            playlistRepository.delete(playlist);

            return ResponseEntity.ok(new ApiResponseDTO<>(true, "Playlist deleted successfully", null));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    public ResponseEntity<ApiResponseDTO<List<PlaylistPreviewDTO>>> searchPlaylists(Pageable pageable, String key) {
        try {
            List<PlaylistPreviewDTO> playlists = getPlaylistsForSearch(pageable, key);
            return ResponseEntity.ok(new ApiResponseDTO<>(true, "Playlists fetched successfully", playlists));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    public List<PlaylistPreviewDTO> getPlaylistsForSearch(Pageable pageable, String key) {
        try {
            return playlistRepository.findByVisibilityAndNameContainingIgnoreCase(
                            PlaylistVisibility.PUBLIC,
                            key == null ? "" : key,
                            pageable
                    )
                    .getContent()
                    .stream()
                    .map(this::toPreview)
                    .toList();
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return List.of();
        }
    }

    private Playlist getOwnedPlaylist(String userId, String playlistId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new RuntimeException("Playlist not found"));

        if (!playlist.getOwnerId().equals(userId)) {
            throw new RuntimeException("Not authorized to access playlist");
        }

        return playlist;
    }

    private PlaylistPreviewDTO toPreview(Playlist playlist) {
        return new PlaylistPreviewDTO(
                playlist.getId(),
                playlist.getName(),
                playlist.getCoverUrl(),
                userPreviewClient.getUserPreview(playlist.getOwnerId()),
                playlist.getVisibility(),
                playlist.isSystem(),
                playlist.getCreatedAt(),
                playlist.getUpdatedAt()
        );
    }
}
