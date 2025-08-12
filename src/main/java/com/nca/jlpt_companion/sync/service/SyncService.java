package com.nca.jlpt_companion.sync.service;

import com.nca.jlpt_companion.sync.dto.UploadLogsRequest;
import com.nca.jlpt_companion.sync.dto.UploadLogsResponse;
import com.nca.jlpt_companion.sync.model.ChangeLogEntity;
import com.nca.jlpt_companion.sync.repo.ChangeLogRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SyncService {

    private final ChangeLogRepo changeLogRepo;

    /** Guard untuk perbedaan waktu device vs server saat upload log. */
    private static final Duration MAX_SKEW = Duration.ofHours(48);

    /**
     * Proses upload logs idempotent per item.
     *
     * @param userId                diambil dari JWT (Authentication principal)
     * @param req                   body request (tanpa userId)
     * @param batchId               optional; dari body
     * @param batchIdempotencyKey   optional; dari header "Idempotency-Key" (dipakai untuk observability di masa depan)
     */
    public UploadLogsResponse uploadLogs(UUID userId,
                                         UploadLogsRequest req,
                                         String batchId,
                                         String batchIdempotencyKey) {

        var duplicated = new ArrayList<String>();
        var conflicts  = new ArrayList<UploadLogsResponse.Conflict>();
        int stored = 0;

        // Validasi ringan request
        if (req == null || req.logs() == null || req.logs().isEmpty()) {
            return new UploadLogsResponse(true, 0, List.of(), List.of());
        }
        if (req.deviceId() == null) {
            conflicts.add(new UploadLogsResponse.Conflict(-1, "device_id_missing"));
            return new UploadLogsResponse(false, 0, List.of(), conflicts);
        }

        var now = OffsetDateTime.now();

        for (int i = 0; i < req.logs().size(); i++) {
            var item = req.logs().get(i);

            // 1) Validasi required fields
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

            // 2) Guard clock skew
            var dt = Duration.between(item.occurredAt(), now).abs();
            if (dt.compareTo(MAX_SKEW) > 0) {
                conflicts.add(new UploadLogsResponse.Conflict(i, "clock_skew_exceeded"));
                continue;
            }

            // 3) Idempotency check
            if (changeLogRepo.existsByUserIdAndDeviceIdAndIdempotencyKey(
                    userId, req.deviceId(), item.idempotencyKey())) {
                duplicated.add(item.idempotencyKey());
                continue;
            }

            // 4) Persist as raw change log (append-only)
            var row = new ChangeLogEntity(
                    UUID.randomUUID(),
                    userId,
                    req.deviceId(),
                    item.kind(),
                    item.occurredAt(),
                    item.payload(),          // JsonNode → jsonb (Hibernate 6 JSON mapping)
                    item.idempotencyKey()
            );
            changeLogRepo.save(row);
            stored++;
        }

        return new UploadLogsResponse(true, stored, duplicated, conflicts);
    }

    // ===== helpers =====

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
