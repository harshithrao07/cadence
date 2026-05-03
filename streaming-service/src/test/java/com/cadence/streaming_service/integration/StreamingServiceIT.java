package com.cadence.streaming_service.integration;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.cadence.streaming_service.client.CatalogClient;
import com.cadence.streaming_service.dto.SongStreamingMetadataDTO;
import com.cadence.streaming_service.model.PlayHistory;
import com.cadence.streaming_service.model.PlayHistoryId;
import com.cadence.streaming_service.repository.PlayHistoryRepository;
import com.cadence.streaming_service.service.AwsStreamingService;
import com.cadence.streaming_service.service.StreamingService;
import org.apache.http.client.methods.HttpGet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
    private static final byte[] AUDIO_BYTES = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

    @BeforeEach
    void setUp() {
        playHistoryRepository.deleteAll();
    }

    @Test
    void streamSongById_streamsFromS3_andRecordsPlay_andReturnsPartialContent() throws Exception {
        when(catalogClient.getStreamingMetadata(SONG_ID))
                .thenReturn(new SongStreamingMetadataDTO(SONG_ID, "https://test-bucket.s3.amazonaws.com/" + OBJECT_KEY));
        when(awsStreamingService.extractKeyFromUrl(anyString())).thenReturn(OBJECT_KEY);
        when(awsStreamingService.findByName(OBJECT_KEY)).thenReturn(true);
        when(awsStreamingService.getFileSize(OBJECT_KEY)).thenReturn((long) AUDIO_BYTES.length);
        when(awsStreamingService.getObjectWithRange(OBJECT_KEY, 0L, 9L)).thenReturn(s3ObjectFor(AUDIO_BYTES, "audio/mpeg"));

        ResponseEntity<StreamingResponseBody> response = streamingService.streamSongById(SONG_ID, USER_ID, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 0-9/10");
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("audio/mpeg");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(10L);

        // Drain the StreamingResponseBody lambda to get coverage on the I/O loop
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        assertThat(out.toByteArray()).containsExactly(AUDIO_BYTES);

        // Real workerService.recordPlay went through to MySQL
        PlayHistory ph = playHistoryRepository.findById(new PlayHistoryId(USER_ID, SONG_ID)).orElseThrow();
        assertThat(ph.getPlayCount()).isEqualTo(1L);
    }

    @Test
    void streamSongById_streamsFromS3_withRangeHeader_serves206_andSubrange() throws Exception {
        when(catalogClient.getStreamingMetadata(SONG_ID))
                .thenReturn(new SongStreamingMetadataDTO(SONG_ID, "https://x/" + OBJECT_KEY));
        when(awsStreamingService.extractKeyFromUrl(anyString())).thenReturn(OBJECT_KEY);
        when(awsStreamingService.findByName(OBJECT_KEY)).thenReturn(true);
        when(awsStreamingService.getFileSize(OBJECT_KEY)).thenReturn(100L);
        when(awsStreamingService.getObjectWithRange(OBJECT_KEY, 10L, 20L))
                .thenReturn(s3ObjectFor(new byte[11], null));

        ResponseEntity<StreamingResponseBody> response = streamingService.streamSongById(SONG_ID, USER_ID, "bytes=10-20");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 10-20/100");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(11L);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("audio/mpeg");
        verify(awsStreamingService).getObjectWithRange(OBJECT_KEY, 10L, 20L);
    }

    @Test
    void streamSongById_streamsFromS3_withOpenEndedRange_clampsToFileSize() throws Exception {
        when(catalogClient.getStreamingMetadata(SONG_ID))
                .thenReturn(new SongStreamingMetadataDTO(SONG_ID, "https://x/" + OBJECT_KEY));
        when(awsStreamingService.extractKeyFromUrl(anyString())).thenReturn(OBJECT_KEY);
        when(awsStreamingService.findByName(OBJECT_KEY)).thenReturn(true);
        when(awsStreamingService.getFileSize(OBJECT_KEY)).thenReturn(50L);
        when(awsStreamingService.getObjectWithRange(OBJECT_KEY, 25L, 49L))
                .thenReturn(s3ObjectFor(new byte[25], "audio/mp4"));

        ResponseEntity<StreamingResponseBody> response = streamingService.streamSongById(SONG_ID, USER_ID, "bytes=25-");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 25-49/50");
    }

    @Test
    void streamSongById_streamsFromS3_withInvertedRange_resetsToFullFile() throws Exception {
        when(catalogClient.getStreamingMetadata(SONG_ID))
                .thenReturn(new SongStreamingMetadataDTO(SONG_ID, "https://x/" + OBJECT_KEY));
        when(awsStreamingService.extractKeyFromUrl(anyString())).thenReturn(OBJECT_KEY);
        when(awsStreamingService.findByName(OBJECT_KEY)).thenReturn(true);
        when(awsStreamingService.getFileSize(OBJECT_KEY)).thenReturn(20L);
        when(awsStreamingService.getObjectWithRange(OBJECT_KEY, 0L, 19L))
                .thenReturn(s3ObjectFor(new byte[20], "audio/mpeg"));

        ResponseEntity<StreamingResponseBody> response = streamingService.streamSongById(SONG_ID, USER_ID, "bytes=15-5");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 0-19/20");
    }

    @Test
    void streamSongById_returns404_whenMetadataMissing() {
        when(catalogClient.getStreamingMetadata(SONG_ID)).thenReturn(null);

        ResponseEntity<StreamingResponseBody> response = streamingService.streamSongById(SONG_ID, USER_ID, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(playHistoryRepository.count()).isZero();
    }

    @Test
    void streamSongById_returns404_whenSongUrlIsBlank() {
        when(catalogClient.getStreamingMetadata(SONG_ID))
                .thenReturn(new SongStreamingMetadataDTO(SONG_ID, ""));

        ResponseEntity<StreamingResponseBody> response = streamingService.streamSongById(SONG_ID, USER_ID, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(awsStreamingService, never()).findByName(anyString());
    }

    @Test
    void streamSongById_returns500_whenS3ReadThrows() {
        when(catalogClient.getStreamingMetadata(SONG_ID))
                .thenReturn(new SongStreamingMetadataDTO(SONG_ID, "https://x/" + OBJECT_KEY));
        when(awsStreamingService.extractKeyFromUrl(anyString())).thenReturn(OBJECT_KEY);
        when(awsStreamingService.findByName(OBJECT_KEY)).thenReturn(true);
        when(awsStreamingService.getFileSize(OBJECT_KEY)).thenReturn(100L);
        when(awsStreamingService.getObjectWithRange(anyString(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("s3 down"));

        ResponseEntity<StreamingResponseBody> response = streamingService.streamSongById(SONG_ID, USER_ID, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void streamSongById_skipsS3_andFallsThroughToExternalUrl_whenObjectMissing() {
        // External URL fall-through opens an HttpURLConnection — we can't reach a real host in a test.
        // The service catches the exception and returns 500, which exercises the fall-through branch.
        when(catalogClient.getStreamingMetadata(SONG_ID))
                .thenReturn(new SongStreamingMetadataDTO(SONG_ID, "http://example.invalid/song.mp3"));
        when(awsStreamingService.extractKeyFromUrl(anyString())).thenReturn(OBJECT_KEY);
        when(awsStreamingService.findByName(OBJECT_KEY)).thenReturn(false);

        ResponseEntity<StreamingResponseBody> response = streamingService.streamSongById(SONG_ID, USER_ID, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(awsStreamingService, never()).getObjectWithRange(any(), anyLong(), anyLong());
    }

    private static S3Object s3ObjectFor(byte[] bytes, String contentType) {
        S3Object obj = new S3Object();
        obj.setObjectContent(new S3ObjectInputStream(new ByteArrayInputStream(bytes), new HttpGet()));
        if (contentType != null) {
            ObjectMetadata md = new ObjectMetadata();
            md.setContentType(contentType);
            obj.setObjectMetadata(md);
        } else {
            obj.setObjectMetadata(new ObjectMetadata());
        }
        return obj;
    }
}
