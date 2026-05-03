package com.project.cadence.controller;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.PaginatedResponseDTO;
import com.project.cadence.dto.artist.ArtistPreviewDTO;
import com.project.cadence.filter.InternalTrafficFilter;
import com.project.cadence.service.ArtistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ArtistController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = InternalTrafficFilter.class
        )
)
class ArtistControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean ArtistService artistService;

    private static final String ARTIST_ID = "artist-1";
    private static final String USER_ID   = "user-1";

    // ── GET /api/v1/artist/all ────────────────────────────────────────────────

    @Test
    void getAllArtists_returns200_withDefaultParams() throws Exception {
        ArtistPreviewDTO artist = new ArtistPreviewDTO(ARTIST_ID, "Drake", null);
        PaginatedResponseDTO<ArtistPreviewDTO> page = new PaginatedResponseDTO<>(List.of(artist), 0, 24, 1L, 1, true);
        when(artistService.getAllArtists(0, 24, null)).thenReturn(
                ResponseEntity.ok(new ApiResponseDTO<>(true, "ok", page))
        );

        mockMvc.perform(get("/api/v1/artist/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Drake"));
    }

    @Test
    void getAllArtists_returns200_withKeyFilter() throws Exception {
        PaginatedResponseDTO<ArtistPreviewDTO> page = new PaginatedResponseDTO<>(List.of(), 0, 24, 0L, 0, true);
        when(artistService.getAllArtists(0, 24, "Dr")).thenReturn(
                ResponseEntity.ok(new ApiResponseDTO<>(true, "ok", page))
        );

        mockMvc.perform(get("/api/v1/artist/all").param("key", "Dr"))
                .andExpect(status().isOk());
    }

    // ── GET /api/v1/artist/{artistId} ─────────────────────────────────────────

    @Test
    void getArtistProfile_returns404_whenNotFound() throws Exception {
        when(artistService.getArtistProfile(ARTIST_ID)).thenReturn(
                ResponseEntity.status(404).body(new ApiResponseDTO<>(false, "Artist not found", null))
        );

        mockMvc.perform(get("/api/v1/artist/{id}", ARTIST_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Artist not found"));
    }

    // ── POST /api/v1/artist/{artistId}/follow ─────────────────────────────────

    @Test
    void followArtist_returns200_withUserIdHeader() throws Exception {
        when(artistService.followArtist(ARTIST_ID, USER_ID)).thenReturn(
                ResponseEntity.ok(new ApiResponseDTO<>(true, "Followed", null))
        );

        mockMvc.perform(post("/api/v1/artist/{id}/follow", ARTIST_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void followArtist_returns400_whenUserIdHeaderMissing() throws Exception {
        mockMvc.perform(post("/api/v1/artist/{id}/follow", ARTIST_ID))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/v1/artist/{artistId}/unfollow ───────────────────────────────

    @Test
    void unfollowArtist_returns200_withUserIdHeader() throws Exception {
        when(artistService.unfollowArtist(ARTIST_ID, USER_ID)).thenReturn(
                ResponseEntity.ok(new ApiResponseDTO<>(true, "Unfollowed", null))
        );

        mockMvc.perform(post("/api/v1/artist/{id}/unfollow", ARTIST_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── GET /api/v1/artist/{artistId}/isFollowing ─────────────────────────────

    @Test
    void isFollowing_returns200_withBooleanResult() throws Exception {
        when(artistService.isFollowing(ARTIST_ID, USER_ID)).thenReturn(
                ResponseEntity.ok(new ApiResponseDTO<>(true, "Following", true))
        );

        mockMvc.perform(get("/api/v1/artist/{id}/isFollowing", ARTIST_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }
}
