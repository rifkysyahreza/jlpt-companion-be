package com.nca.jlpt_companion.sync.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UploadLogsRequest(
        UUID deviceId,
        String batchId,
        List<LogItem> logs
) {
    public record LogItem(
            String kind,
            OffsetDateTime occurredAt,
            String idempotencyKey,
            com.fasterxml.jackson.databind.JsonNode payload
    ) {}
}

