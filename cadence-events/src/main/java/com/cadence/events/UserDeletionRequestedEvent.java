package com.cadence.events;

/**
 * Account deletion saga, step 1: the user asked auth-service to delete their account (now DELETING, can't log in).
 * Every service holding data about the user purges it and replies with {@link UserDataPurgedEvent}. Forward-only:
 * nothing is compensated; auth re-sends the request until every service has confirmed.
 */
public record UserDeletionRequestedEvent(String userId) {
}
