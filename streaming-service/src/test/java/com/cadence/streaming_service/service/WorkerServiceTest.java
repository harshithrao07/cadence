package com.cadence.streaming_service.service;

import com.cadence.streaming_service.model.PlayHistory;
import com.cadence.streaming_service.model.PlayHistoryId;
import com.cadence.streaming_service.repository.PlayHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerServiceTest {

    @Mock private PlayHistoryRepository playHistoryRepository;

    @InjectMocks
    private WorkerService workerService;

    private static final String USER_ID = "user-1";
    private static final String SONG_ID = "song-1";

    @Test
    void recordPlay_createsNewEntry_whenNoExistingRecord() {
        PlayHistoryId id = new PlayHistoryId(USER_ID, SONG_ID);
        when(playHistoryRepository.findById(id)).thenReturn(Optional.empty());

        workerService.recordPlay(USER_ID, SONG_ID);

        ArgumentCaptor<PlayHistory> captor = ArgumentCaptor.forClass(PlayHistory.class);
        verify(playHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getPlayCount()).isEqualTo(1L);
        assertThat(captor.getValue().getId().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getId().getSongId()).isEqualTo(SONG_ID);
    }

    @Test
    void recordPlay_incrementsPlayCount_whenEntryAlreadyExists() {
        PlayHistoryId id = new PlayHistoryId(USER_ID, SONG_ID);
        PlayHistory existing = PlayHistory.builder()
                .id(id)
                .playCount(4L)
                .build();
        when(playHistoryRepository.findById(id)).thenReturn(Optional.of(existing));

        workerService.recordPlay(USER_ID, SONG_ID);

        ArgumentCaptor<PlayHistory> captor = ArgumentCaptor.forClass(PlayHistory.class);
        verify(playHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getPlayCount()).isEqualTo(5L);
    }
}
