package com.nca.jlpt_companion.sync.dto;

import com.nca.jlpt_companion.users.model.EntitlementCode;

import java.time.OffsetDateTime;
import java.util.List;

public record PullProgressResponse(
        OffsetDateTime serverTime,
        OffsetDateTime nextSince,
        List<ChangeItem> changes
) {
    public record ChangeItem(
            String kind,            // "entitlement_snapshot" | "entitlement_upsert"
            OffsetDateTime at,
            EntitlementPayload payload
    ) {}

    public record EntitlementPayload(
            EntitlementCode code,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt
    ) {}
}
