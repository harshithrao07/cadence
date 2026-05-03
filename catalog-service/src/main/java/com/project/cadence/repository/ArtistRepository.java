package com.project.cadence.repository;

import com.project.cadence.dto.artist.ArtistPreviewDTO;
import com.project.cadence.model.Artist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArtistRepository extends JpaRepository<Artist, String> {

    Page<Artist> findByNameStartingWithIgnoreCase(String trim, Pageable pageable);

    Page<Artist> findByNameContainingIgnoreCase(String searchKey, Pageable pageable);

    @Query("""
    SELECT new com.project.cadence.dto.artist.ArtistPreviewDTO(
        a.id,
        a.name,
        a.profileUrl
    )
    FROM Song s
    JOIN s.createdBy a
    GROUP BY a.id, a.name, a.profileUrl
    ORDER BY COUNT(s.id) DESC
""")
    List<ArtistPreviewDTO> findPopularArtists(Pageable pageable);

    @Query("""
                SELECT new com.project.cadence.dto.artist.ArtistPreviewDTO(
                    a.id,
                    a.name,
                    a.profileUrl
                )
                FROM Song s
                JOIN s.createdBy a
                JOIN s.genres g
                WHERE g.id IN :genreIds
                  AND a.id NOT IN :followedArtistIds
                GROUP BY a.id, a.name, a.profileUrl
                ORDER BY COUNT(s.id) DESC
            """)
    List<ArtistPreviewDTO> findSuggestedArtists(
            @Param("genreIds") List<String> genreIds,
            @Param("followedArtistIds") List<String> followedArtistIds,
            Pageable pageable
    );

}
