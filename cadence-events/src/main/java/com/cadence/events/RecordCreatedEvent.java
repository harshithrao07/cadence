package com.cadence.events;

import java.util.List;

public record RecordCreatedEvent(
        String recordId,
        String recordTitle,
        List<String> artists,
        String coverUrl,
        List<String> followerEmails
) {
    public RecordCreatedEvent {
        artists = artists == null ? List.of() : List.copyOf(artists);
        followerEmails = followerEmails == null ? List.of() : List.copyOf(followerEmails);
    }
}
