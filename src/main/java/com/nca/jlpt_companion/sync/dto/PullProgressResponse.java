package com.nca.jlpt_companion.sync.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Kontrak respons /sync/pull-progress sesuai contoh:
 * {
 *   "serverTime": "...",
 *   "nextSince": "...",
 *   "entitlements": { "mode":"snapshot", "items":[{ "kind":"entitlement_snapshot", "at":"...", "payload":{...} }] },
 *   "progress": { "mode":"snapshot|delta", "daily":[ PullProgressV1.DailyItem... ] },
 *   "content": { "latestVersion": 1, "checksum":"sha256:...", "sizeHint": 559 }
 * }
 */
public record PullProgressResponse(
        OffsetDateTime serverTime,
        OffsetDateTime nextSince,
        EntitlementsBlock entitlements,
        ProgressBlock progress,
        ContentPointer content
) {
    // ===== entitlements =====
    public record EntitlementsBlock(
            String mode,                               // "snapshot"
            List<EntitlementItem> items
    ) {}

    public record EntitlementItem(
            String kind,                               // "entitlement_snapshot"
            OffsetDateTime at,
            EntitlementPayload payload
    ) {}

    public record EntitlementPayload(
            String code,                               // premium_all | level_N4 | level_N3 | ssw_access
            OffsetDateTime startsAt,
            OffsetDateTime endsAt
    ) {}

    // ===== progress =====
    public record ProgressBlock(
            String mode,                               // "snapshot" | "delta"
            List<PullProgressV1.DailyItem> daily       // gunakan DTO yang sudah ada
    ) {}

    // ===== content pointer =====
    public record ContentPointer(
            int latestVersion,
            String checksum,
            Long sizeHint
    ) {}
}
