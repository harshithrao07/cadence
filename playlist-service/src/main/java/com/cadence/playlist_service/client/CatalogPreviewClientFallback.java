package com.cadence.playlist_service.client;

import com.cadence.playlist_service.dto.EachSongDTO;
import com.cadence.playlist_service.dto.SongPreviewRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fallback when catalog-service is down, slow, or returning errors.
 *
 * <p>Returns an empty song list — playlists render without song details rather than 500.
 * The {@code songExists} default method calls this and gets {@code false} when the catalog
 * is unreachable, which causes {@code addSongToPlaylist} to throw "Song not found" — that's
 * a safer failure mode than silently adding unverifiable IDs.
 */
@Slf4j
@Component
public class CatalogPreviewClientFallback implements CatalogPreviewClient {

    @Override
    public List<EachSongDTO> getSongPreviews(SongPreviewRequestDTO request) {
        int requested = request == null || request.songIds() == null ? 0 : request.songIds().size();
        log.warn("Circuit breaker open for catalog-service.getSongPreviews(count={}); returning empty list", requested);
        return List.of();
    }
}
