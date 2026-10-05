package com.cadence.events;

/**
 * Registration saga reply: playlist-service could not provision the user. auth-service marks the user FAILED
 * (compensation); they may register again.
 */
public record LikedSongsFailedEvent(String userId, String reason) {
}
