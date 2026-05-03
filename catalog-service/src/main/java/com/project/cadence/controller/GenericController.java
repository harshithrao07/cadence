package com.project.cadence.controller;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.generic.DiscoverDTO;
import com.project.cadence.dto.generic.GlobalSearchDTO;
import com.project.cadence.service.GenericService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class GenericController {
    private final GenericService genericService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponseDTO<GlobalSearchDTO>> getSearchResponse(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam String key
    ) {
        return genericService.getSearchResponse(page, size, key);
    }

    @GetMapping("/discover")
    public ResponseEntity<ApiResponseDTO<DiscoverDTO>> getDiscoveryFeed(
            @RequestHeader("X-User-Id") String userId
    ) {
        return genericService.getDiscoveryFeed(userId);
    }
}
