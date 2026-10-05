package com.project.cadence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * A user following an artist. Owned by catalog-service (formerly a join table mapped by auth-service's User).
 * Queried with JdbcTemplate in ArtistService; mapped here so the table belongs to catalog's schema.
 */
@Entity
@Table(
        name = "artist_following",
        indexes = {
                @Index(name = "idx_artist_following_artist_id", columnList = "artist_id"),
                @Index(name = "idx_artist_following_user_id", columnList = "user_id")
        }
)
@IdClass(ArtistFollow.Key.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ArtistFollow {
    @Id
    @Column(name = "user_id")
    private String userId;

    @Id
    @Column(name = "artist_id")
    private String artistId;

    /** Position in the user's followed-artists list. */
    @Column(name = "follow_order", nullable = false)
    private int followOrder;

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private String userId;
        private String artistId;
    }
}
