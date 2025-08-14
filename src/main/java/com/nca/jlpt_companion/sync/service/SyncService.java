package com.nca.jlpt_companion.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nca.jlpt_companion.common.config.AppSyncProperties;
import com.nca.jlpt_companion.common.exception.AppExceptions;
import com.nca.jlpt_companion.content.repo.CardsRepo;
import com.nca.jlpt_companion.progress.model.ProgressSnapshotEntity;
import com.nca.jlpt_companion.progress.repo.ProgressSnapshotRepo;
import com.nca.jlpt_companion.sync.dto.UploadLogsRequest;
import com.nca.jlpt_companion.sync.dto.UploadLogsResponse;
import com.nca.jlpt_companion.sync.model.ChangeLogEntity;
import com.nca.jlpt_companion.sync.model.ReviewGrade;
import com.nca.jlpt_companion.sync.model.ReviewLogEntity;
import com.nca.jlpt_companion.sync.repo.ChangeLogRepo;
import com.nca.jlpt_companion.sync.repo.ReviewLogRepo;
import com.nca.jlpt_companion.users.repo.UserDeviceRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SyncService {

    private final ChangeLogRepo changeLogRepo;
    private final ReviewLogRepo reviewLogRepo;
    private final ProgressSnapshotRepo progressRepo;
    private final UserDeviceRepo userDeviceRepo;
    private final ObjectMapper om;
    private final AppSyncProperties syncProps;
    private final CardsRepo cardsRepo;

    private enum MatResult { STORED, DUPLICATE, INVALID_CARD, CARD_NOT_FOUND }

    /** Guard perbedaan waktu device↔server saat upload log. */
    private static final Duration MAX_SKEW = Duration.ofHours(48);

    /**
     * Proses upload logs:
     * - Validasi device milik user
     * - Idempotensi per-item (raw change_logs)
     * - Materialisasi review_log ke review_logs (dengan idempotensi final)
     * - Update progress harian (progress_snapshots)
     */
    @Transactional
    public UploadLogsResponse uploadLogs(UUID userId, UploadLogsRequest req, String batchId, String batchKey) {
        var now = OffsetDateTime.now();
        var maxSkew = Duration.ofHours(syncProps.getMaxSkewHours());

        var duplicated = new ArrayList<String>();
        var conflicts  = new ArrayList<UploadLogsResponse.Conflict>();
        int stored = 0;

        if (req == null || req.logs() == null || req.logs().isEmpty()) {
            return new UploadLogsResponse(true, 0, List.of(), List.of());
        }
        if (req.deviceId() == null) {
            conflicts.add(new UploadLogsResponse.Conflict(-1, "device_id_missing"));
            return new UploadLogsResponse(false, 0, List.of(), conflicts);
        }
        // ✅ pastikan device milik user
        if (!userDeviceRepo.existsByIdAndUserId(req.deviceId(), userId)) {
            throw new AppExceptions.ForbiddenException("Device not registered for this user");
        }

        for (int i = 0; i < req.logs().size(); i++) {
            var item = req.logs().get(i);

            // required fields
            if (isBlank(item.kind())) {
                conflicts.add(new UploadLogsResponse.Conflict(i, "kind_missing"));
                continue;
            }
            if (item.occurredAt() == null) {
                conflicts.add(new UploadLogsResponse.Conflict(i, "occurred_at_missing"));
                continue;
            }
            if (isBlank(item.idempotencyKey())) {
                conflicts.add(new UploadLogsResponse.Conflict(i, "idempotency_key_missing"));
                continue;
            }

            // clock skew guard
            var dt = Duration.between(item.occurredAt(), now).abs();
            if (dt.compareTo(maxSkew) > 0) {
                conflicts.add(new UploadLogsResponse.Conflict(i, "clock_skew_exceeded"));
                continue;
            }

            // idempotensi raw
            if (changeLogRepo.existsByUserIdAndDeviceIdAndIdempotencyKey(
                    userId, req.deviceId(), item.idempotencyKey())) {
                duplicated.add(item.idempotencyKey());
                continue;
            }

            // simpan RAW (append-only)
            var raw = new ChangeLogEntity(
                    UUID.randomUUID(),
                    userId,
                    req.deviceId(),
                    item.kind(),
                    item.occurredAt(),
                    item.payload(),       // JsonNode ↔ jsonb (Hibernate 6 JSON)
                    item.idempotencyKey()
            );
            changeLogRepo.save(raw);

            // materialisasi hanya untuk review_log
            if ("review_log".equalsIgnoreCase(item.kind())) {
                MatResult r = materializeReviewLog(userId, req.deviceId(), item);
                switch (r) {
                    case STORED -> stored++;
                    case DUPLICATE -> { /* no-op */ }
                    case INVALID_CARD -> conflicts.add(new UploadLogsResponse.Conflict(i, "invalid_card_id"));
                    case CARD_NOT_FOUND -> conflicts.add(new UploadLogsResponse.Conflict(i, "card_not_found"));
                }
            } else {
                stored++;
            }
        }

        return new UploadLogsResponse(true, stored, duplicated, conflicts);
    }

    /**
     * Materialisasi satu item review_log ke tabel final (skema V2).
     * Idempotent pada (user_id, device_id, idempotency_key).
     */
    private MatResult materializeReviewLog(UUID userId, UUID deviceId, UploadLogsRequest.LogItem item) {
        JsonNode p = item.payload() == null ? om.createObjectNode() : item.payload();

        // parse cardId
        UUID cardUuid;
        try {
            String cardStr = p.path("cardId").asText(null);
            if (cardStr == null) return MatResult.INVALID_CARD;
            cardUuid = UUID.fromString(cardStr);
        } catch (IllegalArgumentException ex) {
            return MatResult.INVALID_CARD;
        }

        // pre-check: idempotensi final
        if (reviewLogRepo.existsByUserIdAndDeviceIdAndIdempotencyKey(userId, deviceId, item.idempotencyKey())) {
            return MatResult.DUPLICATE;
        }

        // ✅ pre-check: card harus ada
        if (!cardsRepo.existsById(cardUuid)) {
            return MatResult.CARD_NOT_FOUND;
        }

        // parse grade → enum
        ReviewGrade gradeEnum = parseGrade(p);

        Integer responseMs = p.path("responseMs").isNumber() ? Integer.valueOf(p.path("responseMs").asInt())
                : (p.path("latencyMs").isNumber() ? p.path("latencyMs").asInt() : null);
        Integer prevInterval = p.path("previousInterval").isNumber() ? p.path("previousInterval").asInt() : null;
        Integer nextInterval = p.path("nextInterval").isNumber() ? p.path("nextInterval").asInt() : null;

        var finalRow = new ReviewLogEntity(
                UUID.randomUUID(), userId, deviceId, cardUuid,
                gradeEnum, prevInterval, nextInterval,
                responseMs, item.occurredAt(), p, item.idempotencyKey()
        );
        reviewLogRepo.save(finalRow);

        boolean correct = (gradeEnum != ReviewGrade.AGAIN);
        var day = item.occurredAt().atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
        upsertDailyStats(userId, day, correct, responseMs == null ? 0 : responseMs);

        return MatResult.STORED;
    }

    private ReviewGrade parseGrade(JsonNode p) {
        String gradeStr = p.path("grade").asText(null);
        if (gradeStr == null || gradeStr.isBlank()) {
            boolean correct = p.path("correct").asBoolean(true);
            return correct ? ReviewGrade.GOOD : ReviewGrade.AGAIN;
        }
        try {
            return ReviewGrade.valueOf(gradeStr.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            boolean correct = p.path("correct").asBoolean(true);
            return correct ? ReviewGrade.GOOD : ReviewGrade.AGAIN;
        }
    }

    /** Upsert progress harian (reviews, correct, accuracy, time_sec, new). */
    private void upsertDailyStats(UUID userId, LocalDate day, boolean correct, int responseMs) {
        var opt = progressRepo.findByUserIdAndDay(userId, day);

        int addReviews = 1;
        int addCorrect = correct ? 1 : 0;
        int addTimeSec = Math.max(0, responseMs / 1000);

        if (opt.isEmpty()) {
            var stats = new LinkedHashMap<String, Object>();
            stats.put("reviews", addReviews);
            stats.put("correct", addCorrect);
            stats.put("accuracy", addCorrect == 0 ? 0.0 : 1.0); // 1/1 bila correct
            stats.put("time_sec", addTimeSec);
            stats.put("new", 0);
            var json = toJson(stats);
            var row = new ProgressSnapshotEntity(UUID.randomUUID(), userId, day, json);
            progressRepo.save(row);
        } else {
            var row = opt.get();
            var node = parseJson(row.getStatsJson());
            int reviews = node.path("reviews").asInt(0) + addReviews;
            int corrects = node.path("correct").asInt(0) + addCorrect;
            int timeSec = node.path("time_sec").asInt(0) + addTimeSec;
            double accuracy = reviews == 0 ? 0.0 : (corrects * 1.0) / reviews;

            var updated = new LinkedHashMap<String, Object>();
            updated.put("reviews", reviews);
            updated.put("correct", corrects);
            updated.put("accuracy", accuracy);
            updated.put("time_sec", timeSec);
            updated.put("new", node.path("new").asInt(0));

            row.withStats(toJson(updated));
            progressRepo.save(row);
        }
    }

    // ===== helpers =====
    private JsonNode parseJson(String s) {
        try { return om.readTree(s == null ? "{}" : s); }
        catch (Exception e) { return om.createObjectNode(); }
    }
    private String toJson(Object o) {
        try { return om.writeValueAsString(o); }
        catch (Exception e) { return "{\"reviews\":0,\"correct\":0,\"accuracy\":0.0,\"time_sec\":0,\"new\":0}"; }
    }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
