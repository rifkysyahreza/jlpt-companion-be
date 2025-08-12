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
@Table(name = "change_logs",
        uniqueConstraints = @UniqueConstraint(name = "uq_change_idemp", columnNames = {"user_id","device_id","idempotency_key"}))
public class ChangeLogEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private JsonNode payload;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    public ChangeLogEntity(UUID id, UUID userId, UUID deviceId, String kind,
                           OffsetDateTime occurredAt, JsonNode payload, String idempotencyKey) {
        this.id = id;
        this.userId = userId;
        this.deviceId = deviceId;
        this.kind = kind;
        this.occurredAt = occurredAt;
        this.payload = payload;
        this.idempotencyKey = idempotencyKey;
    }
}
