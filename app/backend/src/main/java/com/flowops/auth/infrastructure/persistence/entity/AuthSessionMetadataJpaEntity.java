package com.flowops.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_session_metadata")
public class AuthSessionMetadataJpaEntity {
    @Id
    @Column(name = "session_id")
    private String sessionId;

    @Column(nullable = false, unique = true)
    private UUID reference;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "device_summary")
    private String deviceSummary;

    @Column(name = "coarse_location")
    private String coarseLocation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuthSessionMetadataJpaEntity() {}

    public AuthSessionMetadataJpaEntity(
            String sessionId,
            UUID reference,
            UUID userId,
            String ipAddress,
            String deviceSummary,
            String coarseLocation,
            Instant createdAt) {
        this.sessionId = sessionId;
        this.reference = reference;
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.deviceSummary = deviceSummary;
        this.coarseLocation = coarseLocation;
        this.createdAt = createdAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public UUID getReference() {
        return reference;
    }

    public UUID getUserId() {
        return userId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
