package com.agridabao.api.farm;

import tools.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "farm_save")
public class FarmSave {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private long revision;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "generator_version", nullable = false, length = 80)
    private String generatorVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode snapshot;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    @Column(name = "last_logout_at")
    private Instant lastLogoutAt;

    protected FarmSave() {
    }

    public FarmSave(UUID userId, long revision, int schemaVersion,
                    String generatorVersion, JsonNode snapshot, Instant savedAt) {
        this.userId = userId;
        this.revision = revision;
        this.schemaVersion = schemaVersion;
        this.generatorVersion = generatorVersion;
        this.snapshot = snapshot;
        this.savedAt = savedAt;
    }

    public UUID getUserId() { return userId; }
    public long getRevision() { return revision; }
    public int getSchemaVersion() { return schemaVersion; }
    public String getGeneratorVersion() { return generatorVersion; }
    public JsonNode getSnapshot() { return snapshot; }
    public Instant getSavedAt() { return savedAt; }
    public Instant getLastLogoutAt() { return lastLogoutAt; }

    public void replace(long revision, int schemaVersion, String generatorVersion,
                        JsonNode snapshot, Instant savedAt) {
        this.revision = revision;
        this.schemaVersion = schemaVersion;
        this.generatorVersion = generatorVersion;
        this.snapshot = snapshot;
        this.savedAt = savedAt;
    }

    public void setLastLogoutAt(Instant lastLogoutAt) {
        this.lastLogoutAt = lastLogoutAt;
    }
}
