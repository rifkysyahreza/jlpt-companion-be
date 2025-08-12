package com.nca.jlpt_companion.sync.repo;

import com.nca.jlpt_companion.sync.model.ChangeLogEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface ChangeLogRepo extends CrudRepository<ChangeLogEntity, UUID> {
    boolean existsByUserIdAndDeviceIdAndIdempotencyKey(UUID userId, UUID deviceId, String idempotencyKey);
}
