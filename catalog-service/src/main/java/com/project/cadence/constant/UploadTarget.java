package com.project.cadence.constant;

import com.cadence.events.MediaUpdatedEvent;

import java.util.Arrays;
import java.util.Optional;

/**
 * Every (table, column) a file upload may write. Clients still name targets as "table column", but only these
 * pairs are accepted, and SQL is built solely from these constants, never from client input.
 * <p>
 * Targets with an {@link #externalTarget()} live in another service's table: catalog stores the file and publishes
 * a {@link MediaUpdatedEvent}; the owning service updates its own row.
 */
public enum UploadTarget {
    SONG_AUDIO("song", "song_url", Access.ADMIN, null),
    RECORD_COVER("record", "cover_url", Access.ADMIN, null),
    ARTIST_PICTURE("artist", "profile_url", Access.ADMIN, null),
    USER_AVATAR("users", "profile_url", Access.SELF, MediaUpdatedEvent.Target.USER_AVATAR),
    PLAYLIST_COVER("playlist", "cover_url", Access.PLAYLIST_OWNER, MediaUpdatedEvent.Target.PLAYLIST_COVER);

    /** Who may upload to / clear a target, besides admins (who may do anything). */
    public enum Access {
        ADMIN,
        /** The row's id is the caller's user id. */
        SELF,
        /** The caller owns the playlist. */
        PLAYLIST_OWNER
    }

    private final String table;
    private final String column;
    private final Access access;
    private final MediaUpdatedEvent.Target externalTarget;

    UploadTarget(String table, String column, Access access, MediaUpdatedEvent.Target externalTarget) {
        this.table = table;
        this.column = column;
        this.access = access;
        this.externalTarget = externalTarget;
    }

    public static Optional<UploadTarget> of(String table, String column) {
        return Arrays.stream(values())
                .filter(t -> t.table.equals(table) && t.column.equals(column))
                .findFirst();
    }

    public String table() {
        return table;
    }

    public String column() {
        return column;
    }

    public Access access() {
        return access;
    }

    public String objectKey(String primaryKey) {
        return table + "/" + column + "/" + primaryKey;
    }

    /** Set for targets owned by another service; null for catalog's own tables. */
    public MediaUpdatedEvent.Target externalTarget() {
        return externalTarget;
    }

    /** Only for catalog-owned targets. */
    public String updateSql() {
        if (externalTarget != null) {
            throw new IllegalStateException(this + " is owned by another service");
        }
        return "UPDATE " + table + " SET " + column + " = ? WHERE id = ?";
    }
}
