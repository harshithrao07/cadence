package com.project.cadence.service;

import com.project.cadence.constant.UploadTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Decides whether a caller may upload to or clear a file target. Admins may touch any target; everyone else only
 * their own avatar and the covers of playlists they own.
 */
@Component
@RequiredArgsConstructor
public class UploadAuthorizer {
    /** Ids are UUIDs or system ids like "LIKED_SONGS_<uuid>"; anything else could escape the object-key prefix. */
    private static final Pattern PRIMARY_KEY = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    private final JdbcTemplate jdbcTemplate;

    public static boolean isValidPrimaryKey(String primaryKey) {
        return primaryKey != null && PRIMARY_KEY.matcher(primaryKey).matches();
    }

    public boolean mayModify(UploadTarget target, String primaryKey, String userId, boolean isAdmin) {
        if (isAdmin) {
            return true;
        }
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return switch (target.access()) {
            case ADMIN -> false;
            case SELF -> userId.equals(primaryKey);
            case PLAYLIST_OWNER -> userId.equals(playlistOwner(primaryKey));
        };
    }

    private String playlistOwner(String playlistId) {
        // TODO(db-per-service phase 2): playlist ownership is verified by playlist-service itself once cover
        // uploads are applied through an event; this read of playlist's table goes away then.
        List<String> owners = jdbcTemplate.queryForList("SELECT user_id FROM playlist WHERE id = ?", String.class, playlistId);
        return owners.isEmpty() ? null : owners.get(0);
    }
}
