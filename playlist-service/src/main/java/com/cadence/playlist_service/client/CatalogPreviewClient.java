package com.cadence.playlist_service.client;

import com.cadence.playlist_service.dto.EachSongDTO;
import com.cadence.playlist_service.dto.SongPreviewRequestDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "catalog-service")
public interface CatalogPreviewClient {

    @PostMapping("/internal/songs/preview")
    List<EachSongDTO> getSongPreviews(@RequestBody SongPreviewRequestDTO request);

    default List<EachSongDTO> getSongPreviews(List<String> songIds) {
        if (songIds.isEmpty()) {
            return List.of();
        }

        return getSongPreviews(new SongPreviewRequestDTO(songIds));
    }

    default boolean songExists(String songId) {
        return !getSongPreviews(List.of(songId)).isEmpty();
    }
}
