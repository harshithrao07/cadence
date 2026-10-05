package com.cadence.events;

public record EmailVerificationEvent(String email, String verificationLink) {
}
