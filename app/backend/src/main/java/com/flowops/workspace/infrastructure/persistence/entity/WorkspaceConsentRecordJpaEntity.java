package com.flowops.workspace.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_consent_record")
public class WorkspaceConsentRecordJpaEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "language", nullable = false)
    private String language;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "consent_text", nullable = false)
    private String consentText;

    @Column(name = "agreed_at", nullable = false)
    private Instant agreedAt;

    protected WorkspaceConsentRecordJpaEntity() {}

    public WorkspaceConsentRecordJpaEntity(
            UUID id, UUID userId, String language, String version, String consentText, Instant agreedAt) {
        this.id = id;
        this.userId = userId;
        this.language = language;
        this.version = version;
        this.consentText = consentText;
        this.agreedAt = agreedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getLanguage() {
        return language;
    }

    public String getVersion() {
        return version;
    }

    public String getConsentText() {
        return consentText;
    }

    public Instant getAgreedAt() {
        return agreedAt;
    }
}
