package com.project.cadence.controller;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.record.RecordPreviewDTO;
import com.project.cadence.filter.InternalTrafficFilter;
import com.project.cadence.model.RecordType;
import com.project.cadence.service.RecordService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RecordController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = InternalTrafficFilter.class
        )
)
class RecordControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean RecordService recordService;

    private static final String RECORD_ID = "record-1";
    private static final String ARTIST_ID = "artist-1";

    private RecordPreviewDTO recordPreview() {
        return new RecordPreviewDTO(RECORD_ID, "Certified Lover Boy", 1000L, null, RecordType.ALBUM, List.of());
    }

    // ── GET /api/v1/record/{recordId} ─────────────────────────────────────────

    @Test
    void getRecordById_returns200_forExistingRecord() throws Exception {
        when(recordService.getRecordById(RECORD_ID)).thenReturn(
                ResponseEntity.ok(new ApiResponseDTO<>(true, "Found", recordPreview()))
        );

        mockMvc.perform(get("/api/v1/record/{id}", RECORD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(RECORD_ID))
                .andExpect(jsonPath("$.data.title").value("Certified Lover Boy"));
    }

    @Test
    void getRecordById_returns404_forMissingRecord() throws Exception {
        when(recordService.getRecordById("bad-record")).thenReturn(
                ResponseEntity.status(404).body(new ApiResponseDTO<>(false, "Record not found", null))
        );

        mockMvc.perform(get("/api/v1/record/{id}", "bad-record"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── DELETE /api/v1/record/delete/{recordId} ───────────────────────────────

    @Test
    void deleteRecord_returns200_onSuccess() throws Exception {
        when(recordService.deleteRecord(RECORD_ID)).thenReturn(
                ResponseEntity.ok(new ApiResponseDTO<>(true, "Deleted", null))
        );

        mockMvc.perform(delete("/api/v1/record/delete/{id}", RECORD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteRecord_returns404_whenRecordNotFound() throws Exception {
        when(recordService.deleteRecord("bad-record")).thenReturn(
                ResponseEntity.status(404).body(new ApiResponseDTO<>(false, "Record not found", null))
        );

        mockMvc.perform(delete("/api/v1/record/delete/{id}", "bad-record"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/record/all?artistId=... ───────────────────────────────────

    @Test
    void getAllRecordsByArtistId_returns200_forExistingArtist() throws Exception {
        when(recordService.getAllRecordsByArtistId(ARTIST_ID)).thenReturn(
                ResponseEntity.ok(new ApiResponseDTO<>(true, "Found", List.of(recordPreview())))
        );

        mockMvc.perform(get("/api/v1/record/all").param("artistId", ARTIST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(RECORD_ID));
    }

    @Test
    void getAllRecordsByArtistId_returns404_forMissingArtist() throws Exception {
        when(recordService.getAllRecordsByArtistId("bad-artist")).thenReturn(
                ResponseEntity.status(404).body(new ApiResponseDTO<>(false, "Artist not found", null))
        );

        mockMvc.perform(get("/api/v1/record/all").param("artistId", "bad-artist"))
                .andExpect(status().isNotFound());
    }
}
