package com.cadence.playlist_service.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "playlist",
        indexes = {
                @Index(name = "idx_playlist_user_id", columnList = "user_id"),
                @Index(name = "idx_playlist_name", columnList = "name")
        }
)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Playlist {
    @Id
    @EqualsAndHashCode.Include
    @ToString.Include
    private String id;

    @Column(nullable = false)
    @ToString.Include
    private String name;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "user_id", nullable = false)
    private String ownerId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlaylistVisibility visibility = PlaylistVisibility.PUBLIC;

    @Builder.Default
    @Column(name = "is_system", updatable = false, nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean isSystem = false;

    @Builder.Default
    @ElementCollection
    @CollectionTable(
            name = "playlist_songs",
            joinColumns = @JoinColumn(name = "playlist_id", referencedColumnName = "id"),
            indexes = {
                    @Index(name = "idx_playlist_songs_playlist_id", columnList = "playlist_id"),
                    @Index(name = "idx_playlist_songs_song_id", columnList = "song_id"),
                    @Index(name = "idx_playlist_songs_playlist_id_song_order", columnList = "playlist_id, song_order")
            },
            uniqueConstraints = {
                    @UniqueConstraint(columnNames = {"playlist_id", "song_id"})
            }
    )
    @Column(name = "song_id", nullable = false)
    @OrderColumn(name = "song_order")
    private List<String> songIds = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    private SystemPlaylistType systemType;

    @PrePersist
    private void assignIdIfMissing() {
        if (isSystem) {
            if (systemType == null) {
                throw new IllegalStateException("systemType must be set for system playlists");
            }

            this.id = systemType.name() + "_" + ownerId;
            return;
        }

        if (id == null || id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
