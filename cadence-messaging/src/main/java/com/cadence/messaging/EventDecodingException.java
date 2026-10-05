package com.cadence.messaging;

/**
 * A message that can never be processed (malformed JSON, wrong shape). Not retried: goes straight to the DLT.
 */
public class EventDecodingException extends RuntimeException {
    public EventDecodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
