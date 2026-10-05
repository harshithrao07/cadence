package com.cadence.auth_service.client;

import com.cadence.auth_service.dto.artist.ArtistPreviewDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fallback when catalog-service is down: the profile renders without followed artists.
 */
@Slf4j
@Component
public class CatalogClientFallback implements CatalogClient {

    @Override
    public List<ArtistPreviewDTO> getFollowedArtists(String userId) {
        log.warn("Circuit breaker open for catalog-service.getFollowedArtists(userId={}); returning empty", userId);
        return List.of();
    }
}
