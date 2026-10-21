package com.cadence.streaming_service.integration;

import com.cadence.streaming_service.client.CatalogClient;
import com.cadence.streaming_service.dto.SongStreamUrlDTO;
import com.cadence.streaming_service.dto.SongStreamingMetadataDTO;
import com.cadence.streaming_service.model.PlayHistory;
import com.cadence.streaming_service.model.PlayHistoryId;
import com.cadence.streaming_service.repository.PlayHistoryRepository;
import com.cadence.streaming_service.service.AwsStreamingService;
import com.cadence.streaming_service.service.StreamingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StreamingServiceIT extends BaseIntegrationTest {

    @Autowired StreamingService streamingService;
    @Autowired PlayHistoryRepository playHistoryRepository;

    @MockBean CatalogClient catalogClient;
    @MockBean AwsStreamingService awsStreamingService;

    private static final String USER_ID = "user-1";
    private static final String SONG_ID = "song-1";
    private static final String OBJECT_KEY = "song/song_url/song-1";
    private static final String OBJECT_URL = "https://test-bucket.s3.amazonaws.com/" + OBJECT_KEY;
    private static final String PRESIGNED_URL = OBJECT_URL + "?X-Amz-Signature=sig";

    @BeforeEach
    void setUp() {
        playHistoryRepository.deleteAll();
    }

    @Test
    void getStreamUrl_returnsPresignedUrl_andRecordsPlay_forBucketObject() {
        givenUploadedBucketObject();

        SongStreamUrlDTO streamUrl = streamingService.getStreamUrl(SONG_ID, USER_ID).orElseThrow();

        assertThat(streamUrl.url()).isEqualTo(PRESIGNED_URL);
        assertThat(streamUrl.expiresAt()).isCloseTo(Instant.now().plus(Duration.ofHours(1)), within(1, ChronoUnit.MINUTES));

        // Real workerService.recordPlay went through to MySQL
        PlayHistory ph = playHistoryRepository.findById(new PlayHistoryId(USER_ID, SONG_ID)).orElseThrow();
        assertThat(ph.getPlayCount()).isEqualTo(1L);
    }

    @Test
    void getStreamUrl_recordsOnePlay_perRequest() {
        givenUploadedBucketObject();

        streamingService.getStreamUrl(SONG_ID, USER_ID);
        streamingService.getStreamUrl(SONG_ID, USER_ID);

        PlayHistory ph = playHistoryRepository.findById(new PlayHistoryId(USER_ID, SONG_ID)).orElseThrow();
        assertThat(ph.getPlayCount()).isEqualTo(2L);
    }

    @Test
    void getStreamUrl_returnsSongUrlUnchanged_forExternalHost() {
        String external = "https://prod-1.storage.jamendo.com/?trackid=1&format=mp32";
        when(catalogClient.getStreamingMetadata(SONG_ID)).thenReturn(new SongStreamingMetadataDTO(SONG_ID, external));
        when(awsStreamingService.bucketObjectKey(external)).thenReturn(Optional.empty());

        SongStreamUrlDTO streamUrl = streamingService.getStreamUrl(SONG_ID, USER_ID).orElseThrow();

        assertThat(streamUrl.url()).isEqualTo(external);
        assertThat(streamUrl.expiresAt()).isNull();
        verify(awsStreamingService, never()).generatePresignedGetUrl(anyString(), any());
        assertThat(playHistoryRepository.count()).isEqualTo(1L);
    }

    @Test
    void getStreamUrl_returnsEmpty_andRecordsNothing_whenUploadMissing() {
        when(catalogClient.getStreamingMetadata(SONG_ID)).thenReturn(new SongStreamingMetadataDTO(SONG_ID, OBJECT_URL));
        when(awsStreamingService.bucketObjectKey(OBJECT_URL)).thenReturn(Optional.of(OBJECT_KEY));
        when(awsStreamingService.findByName(OBJECT_KEY)).thenReturn(false);

        assertThat(streamingService.getStreamUrl(SONG_ID, USER_ID)).isEmpty();
        assertThat(playHistoryRepository.count()).isZero();
        verify(awsStreamingService, never()).generatePresignedGetUrl(anyString(), any());
    }

    @Test
    void getStreamUrl_returnsEmpty_whenMetadataMissing() {
        when(catalogClient.getStreamingMetadata(SONG_ID)).thenReturn(null);

        assertThat(streamingService.getStreamUrl(SONG_ID, USER_ID)).isEmpty();
        assertThat(playHistoryRepository.count()).isZero();
    }

    @Test
    void getStreamUrl_returnsEmpty_whenSongUrlIsBlank() {
        when(catalogClient.getStreamingMetadata(SONG_ID)).thenReturn(new SongStreamingMetadataDTO(SONG_ID, ""));

        assertThat(streamingService.getStreamUrl(SONG_ID, USER_ID)).isEmpty();
        verify(awsStreamingService, never()).bucketObjectKey(anyString());
    }

    private void givenUploadedBucketObject() {
        when(catalogClient.getStreamingMetadata(SONG_ID)).thenReturn(new SongStreamingMetadataDTO(SONG_ID, OBJECT_URL));
        when(awsStreamingService.bucketObjectKey(OBJECT_URL)).thenReturn(Optional.of(OBJECT_KEY));
        when(awsStreamingService.findByName(OBJECT_KEY)).thenReturn(true);
        when(awsStreamingService.generatePresignedGetUrl(eq(OBJECT_KEY), any(Instant.class))).thenReturn(PRESIGNED_URL);
    }
}
