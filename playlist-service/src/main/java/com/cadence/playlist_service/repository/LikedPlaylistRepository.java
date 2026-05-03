package com.cadence.playlist_service.repository;

import com.cadence.playlist_service.model.LikedPlaylist;
import com.cadence.playlist_service.model.LikedPlaylistId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LikedPlaylistRepository extends JpaRepository<LikedPlaylist, LikedPlaylistId> {
    long countByIdUserId(String userId);

    List<LikedPlaylist> findByIdUserIdOrderByLikeOrderAsc(String userId);
}
