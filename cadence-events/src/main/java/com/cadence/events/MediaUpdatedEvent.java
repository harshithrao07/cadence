package com.cadence.events;

/**
 * Published by catalog-service after storing (or deleting) a file whose URL lives in another service's table.
 * The owning service applies it to its own row after checking {@code requestedBy} may modify that row.
 *
 * @param target           what the file is for, see {@link Target}
 * @param targetId         id of the row to update (user id / playlist id)
 * @param url              the new URL, or null when the file was removed
 * @param requestedBy      user id of the uploader
 * @param requestedByAdmin whether the uploader is an admin
 */
public record MediaUpdatedEvent(Target target, String targetId, String url, String requestedBy, boolean requestedByAdmin) {

    public enum Target {
        /** users.profile_url in auth-service */
        USER_AVATAR,
        /** playlist.cover_url in playlist-service */
        PLAYLIST_COVER
    }
}
