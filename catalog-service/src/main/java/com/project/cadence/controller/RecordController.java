package com.project.cadence.controller;

import com.project.cadence.dto.ApiResponseDTO;
import com.project.cadence.dto.record.UpsertRecordDTO;
import com.project.cadence.dto.record.RecordPreviewDTO;
import com.project.cadence.dto.record.UpsertRecordResponseDTO;
import com.project.cadence.service.RecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/record")
public class RecordController {
    private final RecordService recordService;

    @PostMapping("/upsert")
    public ResponseEntity<ApiResponseDTO<UpsertRecordResponseDTO>> upsertNewRecord(@Validated @RequestBody UpsertRecordDTO upsertRecordDTO) {
        return recordService.upsertNewRecord(upsertRecordDTO);
    }

    @DeleteMapping("/delete/{recordId}")
    public ResponseEntity<ApiResponseDTO<Void>> deleteRecord(@PathVariable("recordId") String recordId) {
        return recordService.deleteRecord(recordId);
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponseDTO<List<RecordPreviewDTO>>> getAllRecordsByArtistId(
            @RequestParam String artistId
    ) {
        return recordService.getAllRecordsByArtistId(artistId);
    }

    @GetMapping("/{recordId}")
    public ResponseEntity<ApiResponseDTO<RecordPreviewDTO>> getRecordById(@PathVariable("recordId") String recordId) {
        return recordService.getRecordById(recordId);
    }

}
