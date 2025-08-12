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
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
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
    private String statsJson; // kita parse ringan di service
}
