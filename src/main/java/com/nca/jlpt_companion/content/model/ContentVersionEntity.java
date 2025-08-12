package com.nca.jlpt_companion.content.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.OffsetDateTime;

@Getter
@ToString
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED) // wajib untuk JPA
@Entity
@Table(name = "content_versions")
public class ContentVersionEntity {

    @Id
    @Column(name = "id")
    // BIGSERIAL in DB; we don't insert via JPA for this table, so don't use @GeneratedValue
    private Long id;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
