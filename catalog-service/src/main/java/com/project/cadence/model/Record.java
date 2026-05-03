package com.project.cadence.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "record",
        indexes = {
                @Index(name = "idx_record_record_type", columnList = "record_type"),
                @Index(name = "idx_record_title", columnList = "title")
        }
)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Record {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    @ToString.Include
    private String id;

    @Column(nullable = false)
    @ToString.Include
    private String title;

    @Column(name = "release_timestamp", nullable = false)
    private long releaseTimestamp;

    @Column(name = "cover_url")
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false)
    private RecordType recordType;

    @Builder.Default
    @OneToMany(
            mappedBy = "record",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderColumn(name = "song_order")
    private List<Song> songs = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "artist_records",
            joinColumns = @JoinColumn(name = "record_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "artist_id", referencedColumnName = "id"),
            indexes = {
                    @Index(name = "idx_artist_records_record_id", columnList = "record_id"),
                    @Index(name = "idx_artist_records_artist_id", columnList = "artist_id"),
                    @Index(name = "idx_artist_records_record_id_artist_order", columnList = "record_id, artist_order")
            },
            uniqueConstraints = {
                    @UniqueConstraint(columnNames = {"record_id", "artist_id"})
            }
    )
    @OrderColumn(name = "artist_order")
    @Builder.Default
    private List<Artist> artists = new ArrayList<>();

}
