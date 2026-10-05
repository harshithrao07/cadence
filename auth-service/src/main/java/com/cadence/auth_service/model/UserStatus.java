package com.cadence.auth_service.model;

/**
 * Where a user is in the registration saga. Only ACTIVE users get tokens.
 */
public enum UserStatus {
    /** Created; waiting for other services (playlist) to provision the account. */
    PENDING,
    /** Fully provisioned. */
    ACTIVE,
    /** Provisioning failed or timed out (compensated). The user may register again. */
    FAILED,
    /** Account deletion requested; other services are purging the user's data. The row is deleted when they finish. */
    DELETING
}
