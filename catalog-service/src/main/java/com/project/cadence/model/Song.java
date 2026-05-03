package com.project.cadence.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "song",
        indexes = {
                @Index(name = "idx_song_record_id", columnList = "record_id"),
                @Index(name = "idx_song_title", columnList = "title")
        }
)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Song {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    @ToString.Include
    private String id;

    @Column(nullable = false)
    @ToString.Include
    private String title;

    @Column(name = "song_url")
    private String songUrl;

    @Column(name = "total_duration", nullable = false)
    private int totalDuration;

    @ManyToOne
    @JoinColumn(name = "record_id", referencedColumnName = "id")
    private Record record;

    @Builder.Default
    @ManyToMany
    @JoinTable(
            name = "artist_created_songs",
            joinColumns = @JoinColumn(name = "song_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "artist_id", referencedColumnName = "id"),
            indexes = {
                    @Index(name = "idx_artist_created_songs_song_id", columnList = "song_id"),
                    @Index(name = "idx_artist_created_songs_artist_id", columnList = "artist_id"),
                    @Index(name = "idx_artist_created_songs_song_id_artist_order", columnList = "song_id, artist_order")
            },
            uniqueConstraints = {
                    @UniqueConstraint(columnNames = {"song_id", "artist_id"})
            }
    )
    @OrderColumn(name = "artist_order")
    private List<Artist> createdBy = new ArrayList<>();

    @Builder.Default
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "song_genre",
            joinColumns = @JoinColumn(name = "song_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id", referencedColumnName = "id"),
            indexes = {
                    @Index(name = "idx_song_genre_song_id", columnList = "song_id"),
                    @Index(name = "idx_song_genre_genre_id", columnList = "genre_id")
            },
            uniqueConstraints = {
                    @UniqueConstraint(columnNames = {"song_id", "genre_id"})
            }
    )
    private Set<Genre> genres = new HashSet<>();
}
