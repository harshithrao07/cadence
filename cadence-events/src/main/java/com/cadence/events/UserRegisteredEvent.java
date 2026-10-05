package com.cadence.events;

/**
 * Registration saga, step 1: auth-service created a PENDING user. playlist-service provisions the user's
 * Liked Songs playlist and replies with {@link LikedSongsCreatedEvent} or {@link LikedSongsFailedEvent}.
 */
public record UserRegisteredEvent(String userId) {
}
