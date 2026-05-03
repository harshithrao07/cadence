package com.cadence.streaming_service.service;

import com.cadence.streaming_service.model.PlayHistory;
import com.cadence.streaming_service.model.PlayHistoryId;
import com.cadence.streaming_service.repository.PlayHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkerService {

    private final PlayHistoryRepository playHistoryRepository;

    public void recordPlay(String userId, String songId) {
        PlayHistoryId playHistoryId = new PlayHistoryId(userId, songId);

        PlayHistory playHistory = playHistoryRepository.findById(playHistoryId)
                .orElseGet(() -> PlayHistory.builder()
                        .id(playHistoryId)
                        .playCount(0)
                        .build());

        playHistory.setPlayCount(playHistory.getPlayCount() + 1);
        playHistoryRepository.save(playHistory);
    }
}
