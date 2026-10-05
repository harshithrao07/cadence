package com.project.cadence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Read-only copy of the user fields catalog needs (followers list, release-notification emails), kept in sync from
 * auth-service's {@code auth.user-updated} events. auth-service owns the data; never write it here otherwise.
 */
@Entity
@Table(name = "user_replica")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserReplica {
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    private String email;

    @Column(name = "profile_url")
    private String profileUrl;

    /** occurredAt of the event that produced this snapshot; older snapshots are ignored. */
    @Column(name = "source_updated_at", nullable = false)
    private Instant sourceUpdatedAt;
}
