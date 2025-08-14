package com.nca.jlpt_companion.sync.controller;

import com.nca.jlpt_companion.sync.dto.PullProgressResponse;
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
import java.util.List;
import java.util.UUID;

@Tag(name = "Sync")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncService syncService;
    private final PullProgressService pullProgressService;

    // =========================
    // Upload batched logs
    // =========================
    @PostMapping("/upload-logs")
    @Operation(summary = "Upload batched logs (idempotent; per item & per batch header)")
    public ResponseEntity<UploadLogsResponse> uploadLogs(
            Authentication auth,
            @RequestHeader(value = "Idempotency-Key", required = false) String batchIdempotencyKey,
            @Valid @RequestBody UploadLogsRequest body
    ) {
        UUID userId = (UUID) auth.getPrincipal();
        String batchId = (body.batchId() == null || body.batchId().isBlank()) ? "auto" : body.batchId();
        var res = syncService.uploadLogs(userId, body, batchId, batchIdempotencyKey);
        return ResponseEntity.ok(res);
    }

    // =========================
    // Pull snapshot (entitlements / progress.daily / content pointer)
    // =========================
    @GetMapping("/pull-progress")
    @Operation(summary = "Pull snapshot: entitlements, 14-day progress window, and content pointer")
    public ResponseEntity<PullProgressResponse> pullProgress(
            Authentication auth,
            @RequestParam(defaultValue = "JLPT") String domain,
            @RequestParam(defaultValue = "N5") String level,
            @RequestParam(required = false) String since,   // ISO-8601; optional. If null → snapshot 14 hari
            // `days` disimpan untuk kompatibilitas, tapi diabaikan (window fixed 14)
            @RequestParam(required = false) Integer days,
            // default: sertakan semua blok agar Flutter langsung bisa render
            @RequestParam(value = "include", required = false, defaultValue = "entitlements,progress,content") String includeCsv
    ) {
        UUID userId = (UUID) auth.getPrincipal();
        OffsetDateTime sinceTs = parseSince(since);
        List<String> include = parseInclude(includeCsv);

        var body = pullProgressService.pull(userId, domain, level, sinceTs, days, include);
        return ResponseEntity.ok(body);
    }

    // =========================
    // Helpers
    // =========================
    private OffsetDateTime parseSince(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return OffsetDateTime.parse(s);
        } catch (Exception ignored) {
            return null; // invalid format → treat as snapshot
        }
    }

    private List<String> parseInclude(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .toList();
    }
}
