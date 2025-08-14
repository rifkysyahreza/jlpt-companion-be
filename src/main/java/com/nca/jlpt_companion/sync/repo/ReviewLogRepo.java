package com.nca.jlpt_companion.sync.repo;

import com.nca.jlpt_companion.sync.model.ReviewLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewLogRepo extends JpaRepository<ReviewLogEntity, UUID> {
    boolean existsByUserIdAndDeviceIdAndIdempotencyKey(UUID userId, UUID deviceId, String key);
}
