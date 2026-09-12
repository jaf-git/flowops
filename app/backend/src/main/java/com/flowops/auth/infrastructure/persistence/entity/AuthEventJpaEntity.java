package com.flowops.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_event")
public class AuthEventJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String action;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "subject_email")
    private String subjectEmail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "device_summary")
    private String deviceSummary;

    @Column(name = "coarse_location")
    private String coarseLocation;

    protected AuthEventJpaEntity() {}

    @SuppressWarnings("checkstyle:ParameterNumber")
    public AuthEventJpaEntity(
            UUID id,
            String action,
            UUID actorUserId,
            UUID targetUserId,
            String subjectEmail,
            Instant occurredAt,
            String ipAddress,
            String deviceSummary,
            String coarseLocation) {
        this.id = id;
        this.action = action;
        this.actorUserId = actorUserId;
        this.targetUserId = targetUserId;
        this.subjectEmail = subjectEmail;
        this.occurredAt = occurredAt;
        this.ipAddress = ipAddress;
        this.deviceSummary = deviceSummary;
        this.coarseLocation = coarseLocation;
    }

    public UUID getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public String getSubjectEmail() {
        return subjectEmail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getDeviceSummary() {
        return deviceSummary;
    }

    public String getCoarseLocation() {
        return coarseLocation;
    }
}
