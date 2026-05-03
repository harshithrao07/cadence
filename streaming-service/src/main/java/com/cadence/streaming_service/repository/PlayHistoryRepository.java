package com.cadence.streaming_service.repository;

import com.cadence.streaming_service.model.PlayHistory;
import com.cadence.streaming_service.model.PlayHistoryId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlayHistoryRepository extends JpaRepository<PlayHistory, PlayHistoryId> {
    List<PlayHistory> findByIdUserIdOrderByLastPlayedAtDesc(String userId, Pageable pageable);

    List<PlayHistory> findByIdUserIdOrderByPlayCountDesc(String userId, Pageable pageable);

    @Query("""
            SELECT ph.id.songId AS songId, SUM(ph.playCount) AS playCount
            FROM PlayHistory ph
            GROUP BY ph.id.songId
            ORDER BY SUM(ph.playCount) DESC
    """)
    List<SongPlayCountProjection> findTrendingSongs(Pageable pageable);

    @Query("""
            SELECT ph.id.songId AS songId, SUM(ph.playCount) AS playCount
            FROM PlayHistory ph
            WHERE ph.lastPlayedAt >= :since
            GROUP BY ph.id.songId
            ORDER BY SUM(ph.playCount) DESC
            """)
    List<SongPlayCountProjection> findTrendingSongsSince(@Param("since") java.time.Instant since, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(ph.playCount), 0)
            FROM PlayHistory ph
            WHERE ph.id.songId = :songId
            """)
    long getTotalPlaysBySongId(@Param("songId") String songId);

    long countByIdSongId(String songId);

    @Query("""
            SELECT ph.id.songId AS songId, SUM(ph.playCount) AS playCount
            FROM PlayHistory ph
            WHERE ph.id.songId IN :songIds
            GROUP BY ph.id.songId
            """)
    List<SongPlayCountProjection> findPlayCountsForSongs(@Param("songIds") List<String> songIds);

    @Query("""
            SELECT COUNT(DISTINCT ph.id.userId)
            FROM PlayHistory ph
            WHERE ph.id.songId IN :songIds
              AND (:since IS NULL OR ph.lastPlayedAt >= :since)
            """)
    long countDistinctListenersForSongs(
            @Param("songIds") List<String> songIds,
            @Param("since") java.time.Instant since
    );

    interface SongPlayCountProjection {
        String getSongId();

        long getPlayCount();
    }
}
