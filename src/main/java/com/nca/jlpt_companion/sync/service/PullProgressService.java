package com.nca.jlpt_companion.sync.service;

import com.nca.jlpt_companion.sync.dto.PullProgressResponse;
import com.nca.jlpt_companion.users.model.UserEntitlementEntity;
import com.nca.jlpt_companion.users.repo.UserEntitlementRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PullProgressService {

    private final UserEntitlementRepo entitlementRepo;

    public PullProgressResponse pull(UUID userId, OffsetDateTime since) {
        var now = OffsetDateTime.now();

        // If since null -> send full snapshot (simpler for MVP)
        if (since == null) {
            var all = entitlementRepo.findByUserId(userId);
            var changes = all.stream()
                    .map(this::toSnapshotChange)
                    .toList();
            return new PullProgressResponse(now, now, changes);
        }

        // Simple delta:
        // - new entitlements since 'since' (starts_at > since)
        // - updated entitlements expiration date (ends_at > since)
        var added = entitlementRepo.findByUserIdAndStartsAtAfter(userId, since);
        var extended = entitlementRepo.findByUserIdAndEndsAtAfter(userId, since);

        // merge and remove duplicate IDs if they match
        var combined = Stream.concat(added.stream(), extended.stream())
                .collect(Collectors.toMap(UserEntitlementEntity::getId, e -> e, (a, b)->a))
                .values();

        var changes = combined.stream()
                .map(this::toUpsertChange)
                .toList();

        return new PullProgressResponse(now, now, changes);
    }

    private PullProgressResponse.ChangeItem toSnapshotChange(UserEntitlementEntity e) {
        return new PullProgressResponse.ChangeItem(
                "entitlement_snapshot",
                e.getStartsAt(),
                new PullProgressResponse.EntitlementPayload(e.getCode(), e.getStartsAt(), e.getEndsAt())
        );
    }

    private PullProgressResponse.ChangeItem toUpsertChange(UserEntitlementEntity e) {
        return new PullProgressResponse.ChangeItem(
                "entitlement_upsert",
                e.getStartsAt(), // you can also use now(), but startsAt gives the initial context of the entitlement
                new PullProgressResponse.EntitlementPayload(e.getCode(), e.getStartsAt(), e.getEndsAt())
        );
    }
}
