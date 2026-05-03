package com.cadence.streaming_service.client;

import com.cadence.streaming_service.dto.SongStreamingMetadataDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "catalog-service")
public interface CatalogClient {

    @GetMapping("/internal/songs/{songId}/streaming-metadata")
    SongStreamingMetadataDTO getStreamingMetadata(@PathVariable("songId") String songId);
}
