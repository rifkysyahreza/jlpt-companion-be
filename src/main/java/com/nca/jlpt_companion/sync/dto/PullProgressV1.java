package com.nca.jlpt_companion.sync.dto;

import com.nca.jlpt_companion.users.model.EntitlementCode;

import java.time.OffsetDateTime;
import java.util.List;

public record PullProgressV1(
        OffsetDateTime serverTime,
        OffsetDateTime nextSince,
        Entitlements entitlements,
        Progress progress,
        ContentPointer content
) {
    // === entitlements section ===
    public record Entitlements(
            String mode, // "snapshot" or "delta"
            List<EntitlementItem> items
    ) {}
    public record EntitlementItem(
            String kind, // "entitlement_snapshot" | "entitlement_upsert"
            OffsetDateTime at,
            EntitlementPayload payload
    ) {}
    public record EntitlementPayload(
            EntitlementCode code,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt
    ) {}

    // === progress section ===
    public record Progress(
            String mode, // "snapshot" or "delta"
            List<DailyStat> daily
    ) {}
    public record DailyStat(
            String day,  // "YYYY-MM-DD"
            int reviews,
            int newlyLearned,
            double accuracy,
            int timeSec
    ) {}

    // === content pointer (to help the app know when it needs to update content) ===
    public record ContentPointer(
            long latestVersion,
            String checksum,
            Long sizeHint
    ) {}

    public record DailyItem(
            java.time.LocalDate day,
            int reviews,
            int correct,
            double accuracy,
            int timeSec,
            int newlyLearned
    ) {}
}
