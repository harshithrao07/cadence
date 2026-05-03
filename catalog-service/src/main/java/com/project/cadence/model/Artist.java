package com.project.cadence.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table(
        name = "artist",
        indexes = {
                @Index(name = "idx_artist_name", columnList = "name")
        }
)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Artist {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    @ToString.Include
    private String id;

    @Column(nullable = false, unique = true)
    @ToString.Include
    private String name;

    @Column(name = "profile_url")
    private String profileUrl;

    private String description;

    @Builder.Default
    @ManyToMany(mappedBy = "artists")
    private List<Record> createdRecords = new ArrayList<>();

}

