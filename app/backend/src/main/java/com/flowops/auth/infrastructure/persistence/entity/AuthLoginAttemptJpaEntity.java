package com.flowops.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_login_attempt")
public class AuthLoginAttemptJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String subject;

    @Column(name = "subject_kind", nullable = false)
    private String subjectKind;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    protected AuthLoginAttemptJpaEntity() {}

    public AuthLoginAttemptJpaEntity(UUID id, String subject, String subjectKind, String purpose, Instant attemptedAt) {
        this.id = id;
        this.subject = subject;
        this.subjectKind = subjectKind;
        this.purpose = purpose;
        this.attemptedAt = attemptedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public String getSubjectKind() {
        return subjectKind;
    }

    public String getPurpose() {
        return purpose;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
