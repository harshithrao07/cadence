package com.cadence.streaming_service.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "play_history",
        indexes = {
                @Index(name = "idx_play_history_user_id_song_id", columnList = "user_id, song_id"),
                @Index(name = "idx_play_history_song_id", columnList = "song_id"),
                @Index(name = "idx_play_history_user_id_play_count_desc", columnList = "user_id, play_count DESC"),
                @Index(name = "idx_play_history_user_id_last_played_at_desc", columnList = "user_id, last_played_at DESC"),
                @Index(name = "idx_play_history_song_id_last_played_at", columnList = "song_id, last_played_at")
        }
)
@ToString(onlyExplicitlyIncluded = true)
public class PlayHistory {
    @EmbeddedId
    @ToString.Include
    private PlayHistoryId id;

    @Column(name = "user_id", nullable = false, insertable = false, updatable = false)
    private String userId;

    @Column(name = "song_id", nullable = false, insertable = false, updatable = false)
    private String songId;

    @Column(name = "play_count", nullable = false)
    private long playCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "last_played_at", nullable = false)
    private Instant lastPlayedAt;
}
