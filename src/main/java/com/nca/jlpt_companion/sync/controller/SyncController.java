package com.nca.jlpt_companion.sync.controller;

import com.nca.jlpt_companion.sync.dto.UploadLogsRequest;
import com.nca.jlpt_companion.sync.dto.UploadLogsResponse;
import com.nca.jlpt_companion.sync.service.SyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
@Tag(name = "Sync", description = "Offline-first synchronization")
public class SyncController {

    private final SyncService syncService;

    @Operation(summary = "Upload change logs (idempotent by per-item key)")
    @PostMapping("/upload-logs")
    public ResponseEntity<UploadLogsResponse> uploadLogs(
            @RequestHeader(name = "Idempotency-Key", required = false) String batchIdempotencyKey,
            @Valid @RequestBody UploadLogsRequest request
    ) {
        var resp = syncService.uploadLogs(request, request.batchId(), batchIdempotencyKey);
        return ResponseEntity.ok(resp);
    }
}
