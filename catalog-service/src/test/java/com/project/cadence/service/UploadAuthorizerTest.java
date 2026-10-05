package com.project.cadence.service;

import com.project.cadence.constant.UploadTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.project.cadence.client.PlaylistClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadAuthorizerTest {

    @Mock PlaylistClient playlistClient;
    @InjectMocks UploadAuthorizer authorizer;

    @Test
    void admin_mayModifyEveryTarget() {
        for (UploadTarget target : UploadTarget.values()) {
            assertThat(authorizer.mayModify(target, "x-1", "admin-1", true)).as(target.name()).isTrue();
        }
        verifyNoInteractions(playlistClient);
    }

    @Test
    void user_mayNotModifyCatalogTargets() {
        assertThat(authorizer.mayModify(UploadTarget.SONG_AUDIO, "s-1", "u-1", false)).isFalse();
        assertThat(authorizer.mayModify(UploadTarget.RECORD_COVER, "r-1", "u-1", false)).isFalse();
        assertThat(authorizer.mayModify(UploadTarget.ARTIST_PICTURE, "a-1", "u-1", false)).isFalse();
    }

    @Test
    void user_mayModifyOnlyOwnAvatar() {
        assertThat(authorizer.mayModify(UploadTarget.USER_AVATAR, "u-1", "u-1", false)).isTrue();
        assertThat(authorizer.mayModify(UploadTarget.USER_AVATAR, "u-2", "u-1", false)).isFalse();
    }

    @Test
    void user_mayModifyOnlyCoversOfOwnPlaylists() {
        when(playlistClient.getPlaylistOwner("p-mine")).thenReturn("u-1");
        when(playlistClient.getPlaylistOwner("p-theirs")).thenReturn("u-2");
        // Missing playlist, or playlist-service down (the Feign fallback returns null): deny.
        when(playlistClient.getPlaylistOwner("p-missing")).thenReturn(null);

        assertThat(authorizer.mayModify(UploadTarget.PLAYLIST_COVER, "p-mine", "u-1", false)).isTrue();
        assertThat(authorizer.mayModify(UploadTarget.PLAYLIST_COVER, "p-theirs", "u-1", false)).isFalse();
        assertThat(authorizer.mayModify(UploadTarget.PLAYLIST_COVER, "p-missing", "u-1", false)).isFalse();
    }

    @Test
    void missingUserId_isDenied() {
        assertThat(authorizer.mayModify(UploadTarget.USER_AVATAR, "u-1", null, false)).isFalse();
        assertThat(authorizer.mayModify(UploadTarget.USER_AVATAR, "", "", false)).isFalse();
    }

    @Test
    void primaryKeys_mustBePlainIds() {
        assertThat(UploadAuthorizer.isValidPrimaryKey("LIKED_SONGS_6f1c2d3e-aaaa-bbbb-cccc-1234567890ab")).isTrue();
        assertThat(UploadAuthorizer.isValidPrimaryKey("../song/song_url/x")).isFalse();
        assertThat(UploadAuthorizer.isValidPrimaryKey("a b")).isFalse();
        assertThat(UploadAuthorizer.isValidPrimaryKey("")).isFalse();
        assertThat(UploadAuthorizer.isValidPrimaryKey(null)).isFalse();
    }
}
