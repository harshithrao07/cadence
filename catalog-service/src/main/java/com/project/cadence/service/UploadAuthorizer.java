package com.project.cadence.service;

import com.project.cadence.constant.UploadTarget;
import lombok.RequiredArgsConstructor;
import com.project.cadence.client.PlaylistClient;
import org.springframework.stereotype.Component;

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

    private final PlaylistClient playlistClient;

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
            // Checked before the file is stored, so an unauthorized upload can't overwrite someone's cover in S3;
            // playlist-service checks again before applying the MediaUpdatedEvent.
            case PLAYLIST_OWNER -> userId.equals(playlistClient.getPlaylistOwner(primaryKey));
        };
    }
}
