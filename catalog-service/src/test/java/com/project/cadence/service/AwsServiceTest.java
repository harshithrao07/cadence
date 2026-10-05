package com.project.cadence.service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.project.cadence.constant.UploadTarget;
import com.project.cadence.dto.s3.FileUploadResult;
import com.project.cadence.dto.s3.MetadataDTO;
import jakarta.servlet.http.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwsServiceTest {

    private static final String BUCKET = "test-bucket";

    @Mock AmazonS3 amazonS3;
    @Mock MediaTargetWriter mediaTargetWriter;
    @Mock UploadAuthorizer uploadAuthorizer;

    @InjectMocks AwsService awsService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(awsService, "s3BucketName", BUCKET);
    }

    @Test
    void generateUrl_callsGeneratePresignedUrl_with15MinExpiry() throws Exception {
        URL stubUrl = new URL("https://test-bucket.s3.amazonaws.com/song/abc?signed");
        when(amazonS3.generatePresignedUrl(eq(BUCKET), eq("song/abc"), any(Date.class), eq(HttpMethod.PUT)))
                .thenReturn(stubUrl);

        String result = awsService.generateUrl("song/abc", HttpMethod.PUT);

        assertThat(result).isEqualTo(stubUrl.toString());
        ArgumentCaptor<Date> dateCaptor = ArgumentCaptor.forClass(Date.class);
        verify(amazonS3).generatePresignedUrl(eq(BUCKET), eq("song/abc"), dateCaptor.capture(), eq(HttpMethod.PUT));
        long deltaMs = dateCaptor.getValue().getTime() - System.currentTimeMillis();
        assertThat(deltaMs).isBetween(14L * 60_000, 16L * 60_000);
    }

    @Test
    void findByName_delegatesToDoesObjectExist() {
        when(amazonS3.doesObjectExist(BUCKET, "cover.jpg")).thenReturn(true);

        assertThat(awsService.findByName("cover.jpg")).isTrue();
    }

    @Test
    void getPresignedUrl_buildsKey_andGeneratesUrl_forNonDelete() throws Exception {
        URL stubUrl = new URL("https://signed.test/song/song_url/abc-123");
        when(amazonS3.generatePresignedUrl(eq(BUCKET), eq("song/song_url/abc-123"),
                any(Date.class), eq(HttpMethod.PUT))).thenReturn(stubUrl);

        String result = awsService.getPresignedUrl("song", "song_url", "abc-123", HttpMethod.PUT);

        assertThat(result).isEqualTo(stubUrl.toString());
        verifyNoInteractions(mediaTargetWriter);
    }

    @Test
    void getPresignedUrl_runsDbCleanup_beforeGeneratingUrl_forDeleteMethod() throws Exception {
        URL stubUrl = new URL("https://signed.test/delete");
        when(amazonS3.generatePresignedUrl(eq(BUCKET), eq("artist/profile_url/a-1"),
                any(Date.class), eq(HttpMethod.DELETE))).thenReturn(stubUrl);

        awsService.getPresignedUrl("artist", "profile_url", "a-1", HttpMethod.DELETE);

        // Internal (trusted) callers act as an admin.
        verify(mediaTargetWriter).write(UploadTarget.ARTIST_PICTURE, "a-1", null, null, true);
    }

    @Test
    void handleDeleteDbUpdate_swallowsExceptions_doesNotThrow() {
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(mediaTargetWriter).write(UploadTarget.ARTIST_PICTURE, "a-1", null, "u-1", true);

        awsService.handleDeleteDbUpdate(UploadTarget.ARTIST_PICTURE, "a-1", "u-1", true);
    }

    @Test
    void deleteObject_callsSdkDelete_withBucketAndKey() {
        awsService.deleteObject("song/abc");

        verify(amazonS3).deleteObject(BUCKET, "song/abc");
    }

    @Test
    void deleteObject_swallowsExceptions_doesNotThrow() {
        org.mockito.Mockito.doThrow(new RuntimeException("nope"))
                .when(amazonS3).deleteObject(BUCKET, "song/abc");

        awsService.deleteObject("song/abc");
    }

    @Test
    void getObject_delegatesToSdk() {
        S3Object stub = new S3Object();
        when(amazonS3.getObject(BUCKET, "song/abc")).thenReturn(stub);

        assertThat(awsService.getObject("song/abc")).isSameAs(stub);
    }

    @Test
    void getUrl_returnsBucketUrl_forKey() throws Exception {
        URL url = new URL("https://test-bucket.s3.amazonaws.com/song/abc");
        when(amazonS3.getUrl(BUCKET, "song/abc")).thenReturn(url);

        assertThat(awsService.getUrl("song/abc")).isEqualTo(url.toString());
    }

    @Test
    void extractKeyFromUrl_stripsLeadingSlashFromUriPath() {
        assertThat(awsService.extractKeyFromUrl("https://test-bucket.s3.amazonaws.com/song/abc/song.mp3"))
                .isEqualTo("song/abc/song.mp3");
    }

    @Test
    void extractKeyFromUrl_throwsIllegalArgument_forMalformedUri() {
        assertThatThrownBy(() -> awsService.extractKeyFromUrl("not a uri >"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid song URL");
    }

    @Test
    void getObjectWithRange_buildsRangedRequest_andReturnsObject() {
        S3Object stub = new S3Object();
        when(amazonS3.getObject(any(GetObjectRequest.class))).thenReturn(stub);

        S3Object actual = awsService.getObjectWithRange("song/abc", 100, 500);

        assertThat(actual).isSameAs(stub);
        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(amazonS3).getObject(captor.capture());
        assertThat(captor.getValue().getBucketName()).isEqualTo(BUCKET);
        assertThat(captor.getValue().getKey()).isEqualTo("song/abc");
        assertThat(captor.getValue().getRange()).containsExactly(100L, 500L);
    }

    @Test
    void getFileSize_returnsContentLengthFromMetadata() {
        ObjectMetadata md = new ObjectMetadata();
        md.setContentLength(4096L);
        when(amazonS3.getObjectMetadata(BUCKET, "song/abc")).thenReturn(md);

        assertThat(awsService.getFileSize("song/abc")).isEqualTo(4096L);
    }

    @Test
    void getPresignedUrl_rejectsTargetsOutsideAllowList() {
        assertThatThrownBy(() -> awsService.getPresignedUrl("users", "password_hash", "u-1", HttpMethod.DELETE))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mediaTargetWriter);
    }

    // ── getPresignedUrlForClient (authorization) ──────────────────────────

    @Test
    void presignedForClient_injectionInCategory_isBadRequest_andTouchesNothing() {
        MetadataDTO metadata = new MetadataDTO("users SET role='ADMIN' WHERE 1=1 OR id", "profile_url", "u-1", HttpMethod.DELETE);

        ResponseEntity<String> result = awsService.getPresignedUrlForClient(metadata, "u-1", false);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(mediaTargetWriter, amazonS3);
    }

    @Test
    void presignedForClient_invalidPrimaryKey_isBadRequest() {
        MetadataDTO metadata = new MetadataDTO("users", "profile_url", "../song/song_url/x", HttpMethod.PUT);

        assertThat(awsService.getPresignedUrlForClient(metadata, "u-1", false).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(mediaTargetWriter, amazonS3);
    }

    @Test
    void presignedForClient_notAllowed_isForbidden_andTouchesNothing() {
        MetadataDTO metadata = new MetadataDTO("song", "song_url", "s-1", HttpMethod.PUT);
        when(uploadAuthorizer.mayModify(UploadTarget.SONG_AUDIO, "s-1", "u-1", false)).thenReturn(false);

        assertThat(awsService.getPresignedUrlForClient(metadata, "u-1", false).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(mediaTargetWriter, amazonS3);
    }

    @Test
    void presignedForClient_allowedDelete_clearsColumn_andReturnsUrl() throws Exception {
        MetadataDTO metadata = new MetadataDTO("users", "profile_url", "u-1", HttpMethod.DELETE);
        when(uploadAuthorizer.mayModify(UploadTarget.USER_AVATAR, "u-1", "u-1", false)).thenReturn(true);
        when(amazonS3.generatePresignedUrl(eq(BUCKET), eq("users/profile_url/u-1"), any(Date.class), eq(HttpMethod.DELETE)))
                .thenReturn(new URL("https://signed.test/users/profile_url/u-1"));

        ResponseEntity<String> result = awsService.getPresignedUrlForClient(metadata, "u-1", false);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo("https://signed.test/users/profile_url/u-1");
        verify(mediaTargetWriter).write(UploadTarget.USER_AVATAR, "u-1", null, "u-1", false);
    }

    // ── uploadFileAsync ───────────────────────────────────────────────────

    @Test
    void uploadFileAsync_uploadsNewFile_andReturnsResult_withGeneratedUrl() throws Exception {
        Part part = mock(Part.class, "song uploadPart");
        when(part.getSize()).thenReturn(1234L);
        when(part.getContentType()).thenReturn("audio/mpeg");
        when(part.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[1234]));
        when(amazonS3.doesObjectExist(BUCKET, "song/song_url/abc-123")).thenReturn(false);
        URL url = new URL("https://test-bucket.s3.amazonaws.com/song/song_url/abc-123");
        when(amazonS3.getUrl(BUCKET, "song/song_url/abc-123")).thenReturn(url);

        FileUploadResult result = awsService.uploadFileAsync(UploadTarget.SONG_AUDIO, "abc-123", part).get();

        assertThat(result.tableName()).isEqualTo("song");
        assertThat(result.columnName()).isEqualTo("song_url");
        assertThat(result.primaryKey()).isEqualTo("abc-123");
        assertThat(result.url()).isEqualTo(url.toString());
        verify(amazonS3).putObject(eq(BUCKET), eq("song/song_url/abc-123"), any(), any(ObjectMetadata.class));
        verify(amazonS3, never()).deleteObject(BUCKET, "song/song_url/abc-123");
    }

    @Test
    void uploadFileAsync_deletesExistingObject_beforeUpload_whenObjectExists() throws Exception {
        Part part = mock(Part.class);
        when(part.getSize()).thenReturn(100L);
        when(part.getContentType()).thenReturn("audio/mpeg");
        when(part.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[100]));
        when(amazonS3.doesObjectExist(BUCKET, "song/song_url/abc-123")).thenReturn(true);
        when(amazonS3.getUrl(BUCKET, "song/song_url/abc-123"))
                .thenReturn(new URL("https://x.test/y"));

        awsService.uploadFileAsync(UploadTarget.SONG_AUDIO, "abc-123", part).get();

        verify(amazonS3).deleteObject(BUCKET, "song/song_url/abc-123");
        verify(amazonS3).putObject(eq(BUCKET), eq("song/song_url/abc-123"), any(), any(ObjectMetadata.class));
    }

    @Test
    void uploadFileAsync_returnsResultWithNullUrl_whenSizeIsZero_signalingDelete() throws Exception {
        Part part = mock(Part.class);
        when(part.getSize()).thenReturn(0L);
        when(amazonS3.doesObjectExist(BUCKET, "song/song_url/abc-123")).thenReturn(true);

        FileUploadResult result = awsService.uploadFileAsync(UploadTarget.SONG_AUDIO, "abc-123", part).get();

        assertThat(result.url()).isNull();
        assertThat(result.primaryKey()).isEqualTo("abc-123");
        verify(amazonS3).deleteObject(BUCKET, "song/song_url/abc-123");
        verify(amazonS3, never()).putObject(anyString(), anyString(), any(), any(ObjectMetadata.class));
    }

    // ── save (validates + authorizes every part, then uploads and updates the row) ─────

    @Test
    void save_writesUrlToAllowListedTarget() throws Exception {
        Part p1 = mock(Part.class);
        when(p1.getSize()).thenReturn(50L);
        when(p1.getSubmittedFileName()).thenReturn("record cover_url r-1");
        when(p1.getContentType()).thenReturn("image/png");
        when(p1.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[50]));
        when(uploadAuthorizer.mayModify(UploadTarget.RECORD_COVER, "r-1", "admin-1", true)).thenReturn(true);
        when(amazonS3.doesObjectExist(BUCKET, "record/cover_url/r-1")).thenReturn(false);
        when(amazonS3.getUrl(BUCKET, "record/cover_url/r-1"))
                .thenReturn(new URL("https://test-bucket.s3.amazonaws.com/record/cover_url/r-1"));

        ResponseEntity<List<FileUploadResult>> result = awsService.save("admin-1", true, List.of(p1));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
        verify(mediaTargetWriter).write(UploadTarget.RECORD_COVER, "r-1",
                "https://test-bucket.s3.amazonaws.com/record/cover_url/r-1", "admin-1", true);
    }

    @Test
    void save_targetOutsideAllowList_isBadRequest_andUploadsNothing() {
        // Column names are no longer client-controlled: these used to become UPDATE users SET password_hash = <url>.
        for (String name : List.of("users password_hash u-2", "users role u-1", "genre type g-1", "only_two_parts")) {
            Part part = mock(Part.class);
            when(part.getSubmittedFileName()).thenReturn(name);

            assertThat(awsService.save("u-1", false, List.of(part)).getStatusCode())
                    .as(name).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        verifyNoInteractions(mediaTargetWriter, amazonS3);
    }

    @Test
    void save_anyForbiddenPart_isForbidden_andUploadsNothing() {
        Part own = mock(Part.class);
        when(own.getSubmittedFileName()).thenReturn("users profile_url u-1");
        Part someoneElses = mock(Part.class);
        when(someoneElses.getSubmittedFileName()).thenReturn("users profile_url u-2");
        when(uploadAuthorizer.mayModify(UploadTarget.USER_AVATAR, "u-1", "u-1", false)).thenReturn(true);
        when(uploadAuthorizer.mayModify(UploadTarget.USER_AVATAR, "u-2", "u-1", false)).thenReturn(false);

        assertThat(awsService.save("u-1", false, List.of(own, someoneElses)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(mediaTargetWriter, amazonS3);
    }

    @Test
    void save_returnsOk_withEmptyResults_whenNoPartsSubmitted() {
        ResponseEntity<List<FileUploadResult>> result = awsService.save("u-1", true, List.of());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEmpty();
    }

    private static <T> T mock(Class<T> type) {
        return org.mockito.Mockito.mock(type);
    }

    private static <T> T mock(Class<T> type, String name) {
        return org.mockito.Mockito.mock(type, name);
    }
}
