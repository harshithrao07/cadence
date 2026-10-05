package com.cadence.events;

/**
 * Kafka topic names. Single source of truth for producers and consumers.
 * Every topic carries {@link EventEnvelope} JSON as a string value.
 * <p>
 * Convention: {@code <producing-service>.<event-in-kebab-case>}. Never reuse a pre-envelope name such as
 * {@code user_created}: Kafka treats '.' and '_' as the same character, so {@code user.created} would collide.
 */
public final class Topics {
    public static final String USER_REGISTERED_TOPIC = "auth.user-registered";
    public static final String EMAIL_VERIFICATION_TOPIC = "auth.email-verification";
    public static final String USER_UPDATED_TOPIC = "auth.user-updated";
    public static final String RECORD_CREATED_TOPIC = "catalog.record-created";
    public static final String MEDIA_UPDATED_TOPIC = "catalog.media-updated";
    public static final String LIKED_SONGS_CREATED_TOPIC = "playlist.liked-songs-created";
    public static final String LIKED_SONGS_FAILED_TOPIC = "playlist.liked-songs-failed";

    /** Failed records end up on {@code <topic> + DLT_SUFFIX}. */
    public static final String DLT_SUFFIX = ".DLT";

    private Topics() {
    }
}
