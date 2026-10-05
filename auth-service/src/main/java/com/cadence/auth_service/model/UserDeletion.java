package com.cadence.auth_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Progress of one account deletion saga: which services have confirmed the purge. Holds only the user id (no
 * personal data), so it outlives the user row as a record that the deletion completed.
 */
@Entity
@Table(name = "user_deletions")
@Getter
@Setter
@NoArgsConstructor
public class UserDeletion {
    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    /** When the request was last (re-)published; the retry sweeper re-sends stale ones. */
    @Column(name = "last_requested_at", nullable = false)
    private Instant lastRequestedAt;

    @Column(name = "playlist_purged_at")
    private Instant playlistPurgedAt;

    @Column(name = "catalog_purged_at")
    private Instant catalogPurgedAt;

    @Column(name = "streaming_purged_at")
    private Instant streamingPurgedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public UserDeletion(String userId, Instant now) {
        this.userId = userId;
        this.requestedAt = now;
        this.lastRequestedAt = now;
    }

    public boolean allPurged() {
        return playlistPurgedAt != null && catalogPurgedAt != null && streamingPurgedAt != null;
    }
}
