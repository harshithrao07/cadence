package com.cadence.playlist_service.repository;

import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import com.cadence.playlist_service.model.LikedPlaylist;
import com.cadence.playlist_service.model.LikedPlaylistId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LikedPlaylistRepository extends JpaRepository<LikedPlaylist, LikedPlaylistId> {
    long countByIdUserId(String userId);

    List<LikedPlaylist> findByIdUserIdOrderByLikeOrderAsc(String userId);

    @Modifying
    @Query("DELETE FROM LikedPlaylist lp WHERE lp.id.userId = :userId")
    int deleteAllByUserId(@Param("userId") String userId);

    @Modifying
    @Query("DELETE FROM LikedPlaylist lp WHERE lp.id.playlistId IN :playlistIds")
    int deleteAllByPlaylistIdIn(@Param("playlistIds") Collection<String> playlistIds);
}
