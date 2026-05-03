package com.cadence.streaming_service.service;

import com.cadence.streaming_service.dto.PlayHistoryDTO;
import com.cadence.streaming_service.dto.SongPlayCountDTO;
import com.cadence.streaming_service.dto.SongPlayStatsDTO;
import com.cadence.streaming_service.model.PlayHistory;
import com.cadence.streaming_service.repository.PlayHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlayHistoryService {
    private static final int MAX_PAGE_SIZE = 100;

    private final PlayHistoryRepository playHistoryRepository;

    public List<PlayHistoryDTO> getRecentHistory(String userId, int page, int size) {
        return playHistoryRepository.findByIdUserIdOrderByLastPlayedAtDesc(userId, pageRequest(page, size))
                .stream()
                .map(this::toHistoryDTO)
                .toList();
    }

    public List<PlayHistoryDTO> getUserTopSongs(String userId, int page, int size) {
        return playHistoryRepository.findByIdUserIdOrderByPlayCountDesc(userId, pageRequest(page, size))
                .stream()
                .map(this::toHistoryDTO)
                .toList();
    }

    public List<SongPlayCountDTO> getTrendingSongs(int page, int size) {
        return playHistoryRepository.findTrendingSongs(pageRequest(page, size))
                .stream()
                .map(song -> new SongPlayCountDTO(song.getSongId(), song.getPlayCount()))
                .toList();
    }

    public List<SongPlayCountDTO> getTrendingSongs(Instant since, int page, int size) {
        if (since == null) {
            return getTrendingSongs(page, size);
        }

        return playHistoryRepository.findTrendingSongsSince(since, pageRequest(page, size))
                .stream()
                .map(song -> new SongPlayCountDTO(song.getSongId(), song.getPlayCount()))
                .toList();
    }

    public SongPlayStatsDTO getSongStats(String songId) {
        long totalPlays = playHistoryRepository.getTotalPlaysBySongId(songId);
        long totalListeners = playHistoryRepository.countByIdSongId(songId);
        return new SongPlayStatsDTO(songId, totalPlays, totalListeners);
    }

    public List<SongPlayCountDTO> getSongPlayCounts(List<String> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return List.of();
        }

        return playHistoryRepository.findPlayCountsForSongs(songIds)
                .stream()
                .map(song -> new SongPlayCountDTO(song.getSongId(), song.getPlayCount()))
                .toList();
    }

    public long getUniqueListeners(List<String> songIds, Instant since) {
        if (songIds == null || songIds.isEmpty()) {
            return 0;
        }

        return playHistoryRepository.countDistinctListenersForSongs(songIds, since);
    }

    private PlayHistoryDTO toHistoryDTO(PlayHistory playHistory) {
        return new PlayHistoryDTO(
                playHistory.getId().getUserId(),
                playHistory.getId().getSongId(),
                playHistory.getPlayCount(),
                playHistory.getCreatedAt(),
                playHistory.getLastPlayedAt()
        );
    }

    private Pageable pageRequest(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }
}
