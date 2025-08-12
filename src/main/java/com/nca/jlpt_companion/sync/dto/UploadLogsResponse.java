package com.nca.jlpt_companion.sync.dto;

import java.util.List;

public record UploadLogsResponse(
        boolean accepted,
        int storedCount,
        List<String> duplicatedKeys,
        List<Conflict> conflicts
) {
    public record Conflict(int index, String reason) {}
}
