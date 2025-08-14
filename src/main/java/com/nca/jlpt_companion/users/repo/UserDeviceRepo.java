package com.nca.jlpt_companion.users.repo;

import com.nca.jlpt_companion.users.model.UserDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserDeviceRepo extends JpaRepository<UserDeviceEntity, UUID> {
    boolean existsByIdAndUserId(UUID id, UUID userId);
}
