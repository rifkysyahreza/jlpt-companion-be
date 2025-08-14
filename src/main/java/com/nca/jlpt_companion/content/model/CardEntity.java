package com.nca.jlpt_companion.content.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Getter
@Entity
@Table(name = "cards")
public class CardEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "deck_id", nullable = false)
    private UUID deckId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "headword", nullable = false)
    private String headword;

    @Column(name = "reading")
    private String reading;

    @Column(name = "meaning_id", columnDefinition = "text", nullable = false)
    private String meaningId;

    @JdbcTypeCode(SqlTypes.JSON)                // map ke jsonb
    @Column(name = "metadata", columnDefinition = "jsonb")
    private JsonNode metadata;

    // Kita mapping ke nama itu dulu agar jalan, nanti kita perbaiki via migration.
    @Column(name = "version_id")
    private Long versionId;                     // mengacu ke content_versions.id (BIGINT)

    protected CardEntity() {} // JPA

    public CardEntity(UUID id, UUID deckId, String type, String headword, String reading,
                      UUID meaningId, JsonNode metadata, Long versionId) {
        this.id = id;
        this.deckId = deckId;
        this.type = type;
        this.headword = headword;
        this.reading = reading;
        this.meaningId = String.valueOf(meaningId);
        this.metadata = metadata;
        this.versionId = versionId;
    }

}
