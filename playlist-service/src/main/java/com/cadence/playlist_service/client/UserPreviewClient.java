package com.cadence.playlist_service.client;

import com.cadence.playlist_service.dto.UserPreviewDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "auth-service")
public interface UserPreviewClient {

    @GetMapping("/internal/users/{userId}/preview")
    UserPreviewDTO getUserPreview(@PathVariable("userId") String userId);
}
