package com.cadence.playlist_service.client;

import com.cadence.playlist_service.dto.UserPreviewDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback when auth-service is down, slow, or returning errors.
 *
 * <p>Returns {@code null} — PlaylistService.toPreview tolerates a null owner
 * preview and the playlist still renders without owner name/avatar.
 */
@Slf4j
@Component
public class UserPreviewClientFallback implements UserPreviewClient {

    @Override
    public UserPreviewDTO getUserPreview(String userId) {
        log.warn("Circuit breaker open for auth-service.getUserPreview(userId={}); returning null", userId);
        return null;
    }
}
