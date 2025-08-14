package com.nca.jlpt_companion.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nca.jlpt_companion.content.repo.ContentRepo;
import com.nca.jlpt_companion.progress.model.ProgressSnapshotEntity;
import com.nca.jlpt_companion.progress.repo.ProgressSnapshotRepo;
import com.nca.jlpt_companion.sync.dto.PullProgressResponse;
import com.nca.jlpt_companion.sync.dto.PullProgressV1;
import com.nca.jlpt_companion.users.model.UserEntitlementEntity;
import com.nca.jlpt_companion.users.repo.UserEntitlementRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PullProgressService {

    private static final int WINDOW_DAYS = 14; // jendela snapshot tetap 14 hari

    private final ProgressSnapshotRepo progressRepo;
    private final UserEntitlementRepo entitlementRepo;
    private final ContentRepo contentRepo;
    private final ObjectMapper om;

    /**
     * Bangun snapshot pull-progress.
     * @param sinceTs Jika null → snapshot 14 hari; jika ada → delta sejak tanggal tsb (berbasis hari).
     * @param days    Diabaikan (kita pakai WINDOW_DAYS tetap 14) agar kontrak stabil untuk MVP.
     * @param include Bagian yang ingin di-include: "entitlements", "progress", "content"; null = semua.
     */
    public PullProgressResponse pull(UUID userId,
                                     String domain,
                                     String level,
                                     OffsetDateTime sinceTs,
                                     Integer days,
                                     List<String> include) {

        var serverNow = OffsetDateTime.now();

        // ===== entitlements (snapshot sederhana, aktif per serverNow) =====
        PullProgressResponse.EntitlementsBlock entitlements = null;
        if (include == null || include.contains("entitlements")) {
            var activeEnts = entitlementRepo.findActiveForUser(userId, serverNow);
            var items = new ArrayList<PullProgressResponse.EntitlementItem>();
            for (UserEntitlementEntity e : activeEnts) {
                items.add(new PullProgressResponse.EntitlementItem(
                        "entitlement_snapshot",
                        e.getStartsAt(),
                        new PullProgressResponse.EntitlementPayload(
                                e.getCode().name(),     // enum entitlement_code → string
                                e.getStartsAt(),
                                e.getEndsAt()
                        )
                ));
            }
            entitlements = new PullProgressResponse.EntitlementsBlock("snapshot", items);
        }

        // ===== progress.daily (snapshot 14 hari ASC, atau delta sejak sinceTs) =====
        PullProgressResponse.ProgressBlock progress = null;
        if (include == null || include.contains("progress")) {
            final List<PullProgressV1.DailyItem> daily;

            if (sinceTs == null) {
                // SNAPSHOT: 14 hari terakhir (inklusif), ordered ASC (repo sudah ASC)
                LocalDate sinceDay = serverNow.toLocalDate().minusDays(WINDOW_DAYS - 1L);
                var rows = progressRepo.findSince(userId, sinceDay);
                daily = rows.stream().map(this::mapDaily).toList();
                progress = new PullProgressResponse.ProgressBlock("snapshot", daily);
            } else {
                // DELTA: semua dari sejak hari sinceTs (inklusif), ordered ASC
                LocalDate sinceDay = sinceTs.toLocalDate();
                var rows = progressRepo.findSince(userId, sinceDay);
                daily = rows.stream().map(this::mapDaily).toList();
                progress = new PullProgressResponse.ProgressBlock("delta", daily);
            }
        }

        // ===== content pointer (latest version / checksum / size) =====
        PullProgressResponse.ContentPointer content = null;
        if (include == null || include.contains("content")) {
            int latestVer = contentRepo.findLatestVersionNumber(domain, level);
            Long latestId = contentRepo.findLatestVersionId(domain, level);
            String checksum = (latestId == null) ? "sha256:none" : contentRepo.findDeltaChecksum(latestId, latestId);
            Long size = (latestId == null) ? 0L : contentRepo.findDeltaSize(latestId, latestId);
            content = new PullProgressResponse.ContentPointer(
                    latestVer,
                    checksum == null ? "sha256:none" : checksum,
                    size == null ? 0L : size
            );
        }

        return new PullProgressResponse(
                serverNow,
                serverNow,            // nextSince = serverNow (incremental event belum diaktifkan)
                entitlements,
                progress,
                content
        );
    }

    private PullProgressV1.DailyItem mapDaily(ProgressSnapshotEntity e) {
        JsonNode s = safeJson(e.getStatsJson());
        return new PullProgressV1.DailyItem(
                e.getDay(),
                s.path("reviews").asInt(0),
                s.path("correct").asInt(0),
                s.path("accuracy").asDouble(0.0),
                s.path("time_sec").asInt(0),
                s.path("new").asInt(0)
        );
    }

    private JsonNode safeJson(String s) {
        try { return om.readTree(s == null ? "{}" : s); }
        catch (Exception e) { return om.createObjectNode(); }
    }
}
