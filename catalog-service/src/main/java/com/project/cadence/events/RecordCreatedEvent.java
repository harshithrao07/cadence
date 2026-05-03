package com.project.cadence.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecordCreatedEvent {
    private String recordId;
    private String recordTitle;
    private List<String> artists = new ArrayList<>();
    private String coverUrl;
    private List<String> followerEmails = new ArrayList<>();
}
