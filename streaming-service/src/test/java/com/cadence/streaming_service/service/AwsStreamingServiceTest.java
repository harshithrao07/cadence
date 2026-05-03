package com.cadence.streaming_service.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwsStreamingServiceTest {

    private static final String BUCKET = "test-bucket";

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
    void getObjectWithRange_buildsRangedRequest_andReturnsS3Object() {
        S3Object expected = new S3Object();
        when(amazonS3.getObject(any(GetObjectRequest.class))).thenReturn(expected);

        S3Object actual = awsStreamingService.getObjectWithRange("song/abc", 0, 1024);

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(amazonS3).getObject(captor.capture());
        GetObjectRequest req = captor.getValue();
        assertThat(req.getBucketName()).isEqualTo(BUCKET);
        assertThat(req.getKey()).isEqualTo("song/abc");
        assertThat(req.getRange()).containsExactly(0L, 1024L);
    }

    @Test
    void getFileSize_returnsContentLength_fromMetadata() {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(2_048L);
        when(amazonS3.getObjectMetadata(BUCKET, "song/abc")).thenReturn(metadata);

        assertThat(awsStreamingService.getFileSize("song/abc")).isEqualTo(2_048L);
    }
}
