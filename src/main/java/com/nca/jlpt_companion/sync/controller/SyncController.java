package com.nca.jlpt_companion.sync.controller;

import com.nca.jlpt_companion.sync.dto.PullProgressV1;
import com.nca.jlpt_companion.sync.dto.UploadLogsRequest;
import com.nca.jlpt_companion.sync.dto.UploadLogsResponse;
import com.nca.jlpt_companion.sync.service.PullProgressService;
import com.nca.jlpt_companion.sync.service.SyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
@Tag(name = "Sync", description = "Offline-first synchronization")
public class SyncController {

    private final SyncService syncService;
    private final PullProgressService pullProgressService;

    @Operation(summary = "Upload change logs (idempotent by per-item key)")
    @PostMapping("/upload-logs")
    public ResponseEntity<UploadLogsResponse> uploadLogs(
            Authentication auth,
            @RequestHeader(name = "Idempotency-Key", required = false) String batchIdempotencyKey,
            @Valid @RequestBody UploadLogsRequest request
    ) {
        UUID userId = (UUID) auth.getPrincipal();
        var resp = syncService.uploadLogs(userId, request, request.batchId(), batchIdempotencyKey);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "Pull server→device changes (entitlements/progress/content)")
    @GetMapping("/pull-progress")
    public ResponseEntity<PullProgressV1> pullProgress(
            Authentication auth,
            @RequestParam(value = "since", required = false) OffsetDateTime since,
            @RequestParam(value = "include", required = false, defaultValue = "entitlements,content") String includeCsv,
            @RequestParam(value = "activeOnly", required = false, defaultValue = "true") boolean activeOnly,
            @RequestParam(value = "domain", required = false, defaultValue = "JLPT") String domain,
            @RequestParam(value = "level",  required = false, defaultValue = "N5")   String level
    ) {
        UUID userId = (UUID) auth.getPrincipal();
        var include = new HashSet<>(Arrays.asList(includeCsv.toLowerCase().split(",")));
        var resp = pullProgressService.pull(userId, since, activeOnly, include, domain, level);
        return ResponseEntity.ok(resp);
    }
}
