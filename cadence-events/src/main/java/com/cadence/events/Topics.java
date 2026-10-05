package com.cadence.events;

/**
 * Kafka topic names. Single source of truth for producers and consumers.
 */
public final class Topics {
    public static final String USER_CREATED_TOPIC = "user_created";
    public static final String EMAIL_VERIFICATION_TOPIC = "email_verification";
    public static final String RECORD_CREATED_TOPIC = "record_created";

    private Topics() {
    }
}
