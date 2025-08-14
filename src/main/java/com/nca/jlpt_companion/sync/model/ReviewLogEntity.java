package com.nca.jlpt_companion.sync.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@ToString
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(name = "review_logs",
        uniqueConstraints = @UniqueConstraint(name = "uq_review_idemp",
                columnNames = {"user_id","device_id","idempotency_key"}))
public class ReviewLogEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    /** DB enum review_grade, kita map sebagai String */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "grade", nullable = false, columnDefinition = "review_grade")
    private ReviewGrade grade;

    @Column(name = "previous_interval")
    private Integer previousInterval;

    @Column(name = "next_interval")
    private Integer nextInterval;

    @Column(name = "response_ms")
    private Integer responseMs;

    @Column(name = "reviewed_at", nullable = false)
    private OffsetDateTime reviewedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    public ReviewLogEntity(UUID id, UUID userId, UUID deviceId, UUID cardId,
                           ReviewGrade grade, Integer previousInterval, Integer nextInterval,
                           Integer responseMs, OffsetDateTime reviewedAt,
                           JsonNode payload, String idempotencyKey) {
        this.id = id;
        this.userId = userId;
        this.deviceId = deviceId;
        this.cardId = cardId;
        this.grade = grade;
        this.previousInterval = previousInterval;
        this.nextInterval = nextInterval;
        this.responseMs = responseMs;
        this.reviewedAt = reviewedAt;
        this.payload = payload;
        this.idempotencyKey = idempotencyKey;
    }
}