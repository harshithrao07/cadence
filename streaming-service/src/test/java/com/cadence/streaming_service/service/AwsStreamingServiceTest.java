package com.cadence.streaming_service.service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URL;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwsStreamingServiceTest {

    private static final String BUCKET = "test-bucket";
    private static final String KEY = "song/song_url/abc";
    private static final String OBJECT_URL = "https://test-bucket.s3.amazonaws.com/" + KEY;

    @Mock AmazonS3 amazonS3;

    @InjectMocks AwsStreamingService awsStreamingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(awsStreamingService, "s3BucketName", BUCKET);
    }

    @Test
    void findByName_returnsTrue_whenObjectExists() {
        when(amazonS3.doesObjectExist(BUCKET, "song/abc.mp3")).thenReturn(true);

        assertThat(awsStreamingService.findByName("song/abc.mp3")).isTrue();
    }

    @Test
    void findByName_returnsFalse_whenObjectMissing() {
        when(amazonS3.doesObjectExist(BUCKET, "missing.mp3")).thenReturn(false);

        assertThat(awsStreamingService.findByName("missing.mp3")).isFalse();
    }

    @Test
    void extractKeyFromUrl_stripsLeadingSlash_fromUriPath() {
        String key = awsStreamingService.extractKeyFromUrl("https://test-bucket.s3.amazonaws.com/song/abc/song.mp3");

        assertThat(key).isEqualTo("song/abc/song.mp3");
    }

    @Test
    void extractKeyFromUrl_throwsIllegalArgument_forMalformedUrl() {
        assertThatThrownBy(() -> awsStreamingService.extractKeyFromUrl("not a uri at all >"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid song URL");
    }

    @Test
    void bucketObjectKey_returnsKey_forBucketObjectUrl() throws Exception {
        when(amazonS3.getUrl(BUCKET, KEY)).thenReturn(new URL(OBJECT_URL));

        assertThat(awsStreamingService.bucketObjectKey(OBJECT_URL)).contains(KEY);
    }

    @Test
    void bucketObjectKey_returnsEmpty_forOtherHost() throws Exception {
        when(amazonS3.getUrl(BUCKET, "track.mp3")).thenReturn(new URL("https://test-bucket.s3.amazonaws.com/track.mp3"));

        assertThat(awsStreamingService.bucketObjectKey("https://cdn.example.com/track.mp3")).isEmpty();
    }

    @Test
    void bucketObjectKey_returnsEmpty_whenUrlHasNoPath() {
        assertThat(awsStreamingService.bucketObjectKey("https://cdn.example.com/?trackid=1")).isEmpty();
        assertThat(awsStreamingService.bucketObjectKey("https://cdn.example.com")).isEmpty();
    }

    @Test
    void bucketObjectKey_returnsEmpty_forMalformedUrl() {
        assertThat(awsStreamingService.bucketObjectKey("not a uri at all >")).isEmpty();
    }

    @Test
    void generatePresignedGetUrl_signsGet_untilExpiry() throws Exception {
        Instant expiresAt = Instant.parse("2026-01-01T01:00:00Z");
        URL signed = new URL(OBJECT_URL + "?X-Amz-Signature=sig");
        when(amazonS3.generatePresignedUrl(BUCKET, KEY, Date.from(expiresAt), HttpMethod.GET)).thenReturn(signed);

        assertThat(awsStreamingService.generatePresignedGetUrl(KEY, expiresAt)).isEqualTo(signed.toString());
    }
}
