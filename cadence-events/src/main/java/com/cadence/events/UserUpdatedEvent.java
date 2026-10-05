package com.cadence.events;

/**
 * Snapshot of a user's public profile, published by auth-service whenever it is created or changes. Consumers keep
 * a local replica (catalog-service's {@code user_replica}) by upserting the latest snapshot.
 */
public record UserUpdatedEvent(String userId, String name, String email, String profileUrl) {
}
