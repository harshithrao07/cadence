package com.cadence.events;

/**
 * Registration saga reply: the user's Liked Songs playlist exists. auth-service activates the user.
 */
public record LikedSongsCreatedEvent(String userId, String playlistId) {
}
