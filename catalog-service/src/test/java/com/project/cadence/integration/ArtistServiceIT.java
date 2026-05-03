package com.project.cadence.integration;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.model.Artist;
import com.project.cadence.repository.ArtistRepository;
import com.project.cadence.service.ArtistService;
import com.project.cadence.service.AwsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ArtistServiceIT extends BaseIntegrationTest {

    @Autowired ArtistService artistService;
    @Autowired ArtistRepository artistRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockBean AwsService awsService;

    private String artistId;
    private static final String USER_ID = "user-1";

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM artist_following");
        jdbcTemplate.update("DELETE FROM users");
        artistRepository.deleteAll();

        Artist artist = artistRepository.save(
                Artist.builder().name("Drake").description("Toronto").build()
        );
        artistId = artist.getId();

        jdbcTemplate.update(
                "INSERT INTO users (id, name, profile_url) VALUES (?, ?, ?)",
                USER_ID, "Alice", null
        );
    }

    @Test
    void followFlow_happyPath_persistsAndReports() {
        ResponseEntity<ApiResponseDTO<Void>> follow = artistService.followArtist(artistId, USER_ID);
        assertThat(follow.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiResponseDTO<Boolean>> isFollowing = artistService.isFollowing(artistId, USER_ID);
        assertThat(isFollowing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(isFollowing.getBody()).isNotNull();
        assertThat(isFollowing.getBody().data()).isTrue();

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM artist_following WHERE user_id = ? AND artist_id = ?",
                Integer.class, USER_ID, artistId
        );
        assertThat(rowCount).isEqualTo(1);

        ResponseEntity<ApiResponseDTO<Void>> unfollow = artistService.unfollowArtist(artistId, USER_ID);
        assertThat(unfollow.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiResponseDTO<Boolean>> recheck = artistService.isFollowing(artistId, USER_ID);
        assertThat(recheck.getBody().data()).isFalse();
    }

    @Test
    void followArtist_returns400_whenAlreadyFollowing() {
        artistService.followArtist(artistId, USER_ID);

        ResponseEntity<ApiResponseDTO<Void>> second = artistService.followArtist(artistId, USER_ID);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(second.getBody().message()).isEqualTo("You are already following this artist");
    }

    @Test
    void followArtist_returns404_whenUserDoesNotExist() {
        ResponseEntity<ApiResponseDTO<Void>> result = artistService.followArtist(artistId, "ghost-user");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody().message()).contains("requesting user not found");
    }

    @Test
    void followArtist_returns404_whenArtistDoesNotExist() {
        ResponseEntity<ApiResponseDTO<Void>> result = artistService.followArtist("ghost-artist", USER_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody().message()).isEqualTo("Artist not found");
    }

    @Test
    void followArtist_assignsIncrementingFollowOrder_perUser() {
        Artist second = artistRepository.save(Artist.builder().name("Kendrick").build());
        Artist third = artistRepository.save(Artist.builder().name("Cole").build());

        artistService.followArtist(artistId, USER_ID);
        artistService.followArtist(second.getId(), USER_ID);
        artistService.followArtist(third.getId(), USER_ID);

        Integer max = jdbcTemplate.queryForObject(
                "SELECT MAX(follow_order) FROM artist_following WHERE user_id = ?",
                Integer.class, USER_ID
        );
        assertThat(max).isEqualTo(2);

        Integer distinctOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT follow_order) FROM artist_following WHERE user_id = ?",
                Integer.class, USER_ID
        );
        assertThat(distinctOrders).isEqualTo(3);
    }

    @Test
    void unfollowArtist_returns400_whenNotCurrentlyFollowing() {
        ResponseEntity<ApiResponseDTO<Void>> result = artistService.unfollowArtist(artistId, USER_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody().message()).isEqualTo("You are not following this artist");
    }
}
