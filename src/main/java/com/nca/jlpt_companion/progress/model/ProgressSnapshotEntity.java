package com.nca.jlpt_companion.progress.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@ToString
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED) // tetap protected untuk JPA
@Entity
@Table(name = "progress_snapshots")
public class ProgressSnapshotEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stats", nullable = false, columnDefinition = "jsonb")
    private String statsJson;

    /** Ctor publik untuk pembuatan record baru dari service */
    public ProgressSnapshotEntity(UUID id, UUID userId, LocalDate day, String statsJson) {
        this.id = id;
        this.userId = userId;
        this.day = day;
        this.statsJson = statsJson;
    }

    /** Factory convenience */
    public static ProgressSnapshotEntity create(UUID userId, LocalDate day, String statsJson) {
        return new ProgressSnapshotEntity(UUID.randomUUID(), userId, day, statsJson);
    }

    /** Mutator ringan untuk ganti stats */
    public ProgressSnapshotEntity withStats(String newStatsJson) {
        this.statsJson = newStatsJson;
        return this;
    }
}