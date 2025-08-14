package com.nca.jlpt_companion.users.repo;

import com.nca.jlpt_companion.users.model.UserEntitlementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface UserEntitlementRepo extends JpaRepository<UserEntitlementEntity, UUID> {

    List<UserEntitlementEntity> findByUserId(UUID userId);

    List<UserEntitlementEntity> findByUserIdAndStartsAtAfter(UUID userId, OffsetDateTime since);

    // If the entitlement is extended (ends_at changes), we treat it as an “update” too.
    List<UserEntitlementEntity> findByUserIdAndEndsAtAfter(UUID userId, OffsetDateTime since);

    @Query("""
       SELECT e FROM UserEntitlementEntity e
       WHERE e.userId = ?1
         AND (e.endsAt IS NULL OR e.endsAt > ?2)
       ORDER BY e.startsAt DESC
       """)
    List<UserEntitlementEntity> findActiveForUser(UUID userId, OffsetDateTime now);
}
