package com.project.cadence.constant;

import java.util.Arrays;
import java.util.Optional;

/**
 * Every (table, column) a file upload may write. Clients still name targets as "table column", but only these
 * pairs are accepted, and SQL is built solely from these constants, never from client input.
 */
public enum UploadTarget {
    SONG_AUDIO("song", "song_url", Access.ADMIN),
    RECORD_COVER("record", "cover_url", Access.ADMIN),
    ARTIST_PICTURE("artist", "profile_url", Access.ADMIN),
    USER_AVATAR("users", "profile_url", Access.SELF),
    PLAYLIST_COVER("playlist", "cover_url", Access.PLAYLIST_OWNER);

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

    UploadTarget(String table, String column, Access access) {
        this.table = table;
        this.column = column;
        this.access = access;
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

    public String updateSql() {
        return "UPDATE " + table + " SET " + column + " = ? WHERE id = ?";
    }
}
