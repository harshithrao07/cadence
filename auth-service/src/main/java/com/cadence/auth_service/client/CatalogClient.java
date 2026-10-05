package com.cadence.auth_service.client;

import com.cadence.auth_service.dto.artist.ArtistPreviewDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Artist follows are owned by catalog-service.
 */
@FeignClient(name = "catalog-service", fallback = CatalogClientFallback.class)
public interface CatalogClient {

    @GetMapping("/internal/users/{userId}/followed-artists")
    List<ArtistPreviewDTO> getFollowedArtists(@PathVariable("userId") String userId);
}
