package com.nca.jlpt_companion.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nca.jlpt_companion.content.repo.ContentRepo;
import com.nca.jlpt_companion.progress.model.ProgressSnapshotEntity;
import com.nca.jlpt_companion.progress.repo.ProgressSnapshotRepo;
import com.nca.jlpt_companion.sync.dto.PullProgressV1;
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
    private final ProgressSnapshotRepo progressRepo;
    private final ContentRepo contentRepo;
    private final ObjectMapper om;

    public PullProgressV1 pull(UUID userId, OffsetDateTime since, boolean activeOnly,
                               Set<String> include, String domain, String level) {
        var now = OffsetDateTime.now();

        // ===== entitlements =====
        PullProgressV1.Entitlements entitlementsSection = null;
        if (include.contains("entitlements")) {
            if (since == null) {
                var all = entitlementRepo.findByUserId(userId);
                var items = all.stream()
                        .filter(e -> !activeOnly || isActiveAt(e, now))
                        .map(this::toSnapshotChange)
                        .toList();
                entitlementsSection = new PullProgressV1.Entitlements("snapshot", items);
            } else {
                var added = entitlementRepo.findByUserIdAndStartsAtAfter(userId, since);
                var extended = entitlementRepo.findByUserIdAndEndsAtAfter(userId, since);
                var combined = Stream.concat(added.stream(), extended.stream())
                        .collect(Collectors.toMap(UserEntitlementEntity::getId, e -> e, (a, b) -> a))
                        .values().stream()
                        .filter(e -> !activeOnly || isActiveAt(e, now))
                        .map(this::toUpsertChange)
                        .toList();
                entitlementsSection = new PullProgressV1.Entitlements("delta", combined);
            }
        }

        // ===== progress snapshots =====
        PullProgressV1.Progress progressSection = null;
        if (include.contains("progress")) {
            if (since == null) {
                // ambil semua milik user (atau limit 30 terakhir supaya ringan)
                var all = progressRepo.findAllByUser(userId);
                var limited = all.stream().limit(30).toList(); // cap 30 hari terakhir (bisa diubah)
                var daily = limited.stream().map(this::mapDaily).toList();
                progressSection = new PullProgressV1.Progress("snapshot", daily);
            } else {
                var sinceDay = since.toLocalDate();
                var rows = progressRepo.findSince(userId, sinceDay);
                var daily = rows.stream().map(this::mapDaily).toList();
                progressSection = new PullProgressV1.Progress("delta", daily);
            }
        }

        // ===== content pointer (latest version, scoped) =====
        // MVP: hardcode domain=JLPT & level=N5; later can be taken from user/app preferences
        PullProgressV1.ContentPointer contentPtr = null;
        if (include.contains("content")) {
            int latestVer = contentRepo.findLatestVersionNumber(domain, level);
            if (latestVer <= 0) {
                contentPtr = new PullProgressV1.ContentPointer(0, null, 0L);
            } else {
                Long latestId = contentRepo.findLatestVersionId(domain, level);
                String sum = contentRepo.findDeltaChecksum(latestId, latestId);
                Long size  = contentRepo.findDeltaSize(latestId, latestId);
                contentPtr = new PullProgressV1.ContentPointer(latestVer, sum, size);
            }
        }

        return new PullProgressV1(
                now,
                now,     // untuk MVP kita pakai serverTime sebagai nextSince
                entitlementsSection,
                progressSection,
                contentPtr
        );
    }

    private boolean isActiveAt(UserEntitlementEntity e, OffsetDateTime at) {
        return e.getEndsAt() == null || e.getEndsAt().isAfter(at);
        // startsAt assumed <= now (grant masa depan jarang, bisa ditambah check jika perlu)
    }

    private PullProgressV1.EntitlementItem toSnapshotChange(UserEntitlementEntity e) {
        return new PullProgressV1.EntitlementItem(
                "entitlement_snapshot",
                e.getStartsAt(),
                new PullProgressV1.EntitlementPayload(e.getCode(), e.getStartsAt(), e.getEndsAt())
        );
    }

    private PullProgressV1.EntitlementItem toUpsertChange(UserEntitlementEntity e) {
        return new PullProgressV1.EntitlementItem(
                "entitlement_upsert",
                e.getStartsAt(),
                new PullProgressV1.EntitlementPayload(e.getCode(), e.getStartsAt(), e.getEndsAt())
        );
    }

    private PullProgressV1.DailyStat mapDaily(ProgressSnapshotEntity p) {
        try {
            JsonNode j = om.readTree(p.getStatsJson());
            return new PullProgressV1.DailyStat(
                    p.getDay().toString(),
                    j.path("reviews").asInt(0),
                    j.path("new").asInt(0),
                    j.path("accuracy").asDouble(0.0),
                    j.path("time_sec").asInt(0)
            );
        } catch (Exception ex) {
            // fallback kalau parsing gagal
            return new PullProgressV1.DailyStat(p.getDay().toString(), 0, 0, 0.0, 0);
        }
    }
}
