package com.nca.jlpt_companion.progress.repo;

import com.nca.jlpt_companion.progress.model.ProgressSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgressSnapshotRepo extends JpaRepository<ProgressSnapshotEntity, UUID> {

    /**
     * Ambil snapshot sejak hari tertentu (inklusif), urut naik per hari.
     */
    @Query("SELECT p FROM ProgressSnapshotEntity p " +
            "WHERE p.userId = ?1 AND p.day >= ?2 " +
            "ORDER BY p.day ASC")
    List<ProgressSnapshotEntity> findSince(UUID userId, LocalDate sinceDay);

    /**
     * Ambil semua snapshot milik user, urut terbaru dulu.
     */
    @Query("SELECT p FROM ProgressSnapshotEntity p " +
            "WHERE p.userId = ?1 " +
            "ORDER BY p.day DESC")
    List<ProgressSnapshotEntity> findAllByUser(UUID userId);

    /**
     * Lookup unik untuk (user, day) — dipakai saat upsert harian.
     */
    Optional<ProgressSnapshotEntity> findByUserIdAndDay(UUID userId, LocalDate day);
}
