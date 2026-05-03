package com.cadence.playlist_service.repository;

import com.cadence.playlist_service.model.Playlist;
import com.cadence.playlist_service.model.PlaylistVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaylistRepository extends JpaRepository<Playlist, String> {
    List<Playlist> findByOwnerId(String ownerId);

    List<Playlist> findAllByOwnerIdAndIsSystemFalseOrderByCreatedAtDesc(String ownerId);

    Optional<Playlist> findByIdAndOwnerId(String id, String ownerId);

    Page<Playlist> findByVisibilityAndNameContainingIgnoreCase(
            PlaylistVisibility visibility,
            String name,
            Pageable pageable
    );
}
