package com.cadence.playlist_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "liked_playlists",
        indexes = {
                @Index(name = "idx_liked_playlists_user_id", columnList = "user_id"),
                @Index(name = "idx_liked_playlists_playlist_id", columnList = "playlist_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "playlist_id"})
        }
)
public class LikedPlaylist {
    @EmbeddedId
    private LikedPlaylistId id;

    @Column(name = "like_order")
    private Integer likeOrder;
}
