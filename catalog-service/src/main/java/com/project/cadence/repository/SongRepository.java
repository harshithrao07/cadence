package com.project.cadence.repository;

import com.project.cadence.dto.song.SongBaseDTO;
import com.project.cadence.dto.song.TopSongsInArtistProfileDTO;
import com.project.cadence.model.Artist;
import com.project.cadence.model.Song;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SongRepository extends CrudRepository<Song, String> {
    @Query("""
                SELECT a
                FROM Song s
                JOIN s.createdBy a
                WHERE s.id = :songId
                ORDER BY index(a)
            """)
    List<Artist> findCreatorsBySongId(@Param("songId") String songId);

    @Query("""
                SELECT new com.project.cadence.dto.song.TopSongsInArtistProfileDTO(
                    s.id,
                    s.title,
                    s.totalDuration,
                    r.coverUrl,
                    0L,
                    r.id,
                    r.title
                )
                FROM Song s
                JOIN s.record r
                JOIN s.createdBy a
                WHERE a.id = :artistId
                ORDER BY s.title ASC
            """)
    Page<TopSongsInArtistProfileDTO> findTopSongsForArtist(
            @Param("artistId") String artistId,
            Pageable pageable
    );

    @Query("""
                SELECT s.id
                FROM Song s
                JOIN s.createdBy a
                WHERE a.id = :artistId
            """)
    List<String> findSongIdsByArtistId(@Param("artistId") String artistId);

    Page<Song> findByTitleContainingIgnoreCase(String searchKey, Pageable pageable);

    @Query("""
                SELECT s.id,
                       new com.project.cadence.dto.artist.ArtistPreviewDTO(
                           a.id,
                           a.name,
                           a.profileUrl
                       )
                FROM Song s
                JOIN s.createdBy a
                WHERE s.id IN :songIds
            """)
    List<Object[]> findArtistsForSongs(@Param("songIds") List<String> songIds);

    @Query("""
                SELECT s.id,
                       new com.project.cadence.dto.genre.GenrePreviewDTO(
                           g.id,
                           g.type
                       )
                FROM Song s
                JOIN s.genres g
                WHERE s.id IN :songIds
            """)
    List<Object[]> findGenresForSongs(@Param("songIds") List<String> songIds);

    @Query("""
                SELECT DISTINCT g.id
                FROM Song s
                JOIN s.genres g
                WHERE s.id IN :songIds
            """)
    List<String> findGenreIdsForSongs(@Param("songIds") List<String> songIds);

    @Query("""
                SELECT new com.project.cadence.dto.song.SongBaseDTO(
                    s.id,
                    s.title,
                    s.totalDuration,
                    r.id,
                    r.title,
                    r.coverUrl
                )
                FROM Song s
                JOIN s.record r
                JOIN s.genres g
                WHERE g.id IN :genreIds
                  AND s.id NOT IN :excludedSongIds
                GROUP BY s.id, s.title, s.totalDuration, r.id, r.title, r.coverUrl
                ORDER BY s.title ASC
            """)
    List<SongBaseDTO> findRecommendedFromGenres(
            @Param("genreIds") List<String> genreIds,
            @Param("excludedSongIds") List<String> excludedSongIds,
            Pageable pageable
    );

    @Query("""
                SELECT new com.project.cadence.dto.song.SongBaseDTO(
                    s.id,
                    s.title,
                    s.totalDuration,
                    r.id,
                    r.title,
                    r.coverUrl
                )
                FROM Song s
                JOIN s.record r
                WHERE s.id IN :songIds
            """)
    List<SongBaseDTO> findBaseSongsByIds(@Param("songIds") List<String> songIds);

}
