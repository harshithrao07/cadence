package com.project.cadence.repository;

import com.project.cadence.model.Genre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface GenreRepository extends JpaRepository<Genre, String> {
    boolean existsByType(String type);

    Page<Genre> findByTypeStartingWithIgnoreCase(@Param("key") String key, Pageable pageable);
}
