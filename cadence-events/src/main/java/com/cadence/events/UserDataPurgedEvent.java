package com.cadence.events;

/**
 * Account deletion saga reply: {@code service} has removed everything it held about the user. Once all
 * participants have replied, auth-service deletes the user.
 */
public record UserDataPurgedEvent(String userId, String service) {
}
