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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SyncService {

    private final ChangeLogRepo changeLogRepo;

    // DEV clock-skew guard: 48 jam
    private static final Duration MAX_SKEW = Duration.ofHours(48);

    public UploadLogsResponse uploadLogs(UploadLogsRequest req, String batchId, String batchIdempotencyKey) {
        var duplicated = new ArrayList<String>();
        var conflicts = new ArrayList<UploadLogsResponse.Conflict>();
        int stored = 0;

        var now = OffsetDateTime.now();

        for (int i = 0; i < req.logs().size(); i++) {
            var item = req.logs().get(i);

            // clock skew check (light, dev)
            var dt = Duration.between(item.occurredAt(), now).abs();
            if (dt.compareTo(MAX_SKEW) > 0) {
                conflicts.add(new UploadLogsResponse.Conflict(i, "clock_skew_exceeded"));
                continue;
            }

            // idempotency each item
            if (changeLogRepo.existsByUserIdAndDeviceIdAndIdempotencyKey(
                    req.userId(), req.deviceId(), item.idempotencyKey())) {
                duplicated.add(item.idempotencyKey());
                continue;
            }

            var row = new ChangeLogEntity(
                    UUID.randomUUID(),
                    req.userId(),
                    req.deviceId(),
                    item.kind(),
                    item.occurredAt(),
                    item.payload(),
                    item.idempotencyKey()
            );
            changeLogRepo.save(row);
            stored++;
        }

        return new UploadLogsResponse(true, stored, duplicated, conflicts);
    }
}
