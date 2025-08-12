package com.nca.jlpt_companion.sync.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UploadLogsRequest(
        @NotNull UUID userId, // DEV ONLY: while using userId directly
        @NotNull UUID deviceId,
        String batchId, // optional: batch client
        @NotNull List<LogItem> logs
) {
    public record LogItem(
            @NotBlank String kind, // "review_log", "suspend", ...
            @NotNull OffsetDateTime occurredAt,
            @NotNull JsonNode payload,
            @NotBlank String idempotencyKey // unique each (user,device,key)
    ) {}
}

