package com.cadence.streaming_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PlayHistoryId implements Serializable {

    @Column(name = "user_id")
    private String userId;

    @Column(name = "song_id")
    private String songId;
}

