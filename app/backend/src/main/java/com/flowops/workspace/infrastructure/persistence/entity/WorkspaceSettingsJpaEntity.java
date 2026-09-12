package com.flowops.workspace.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_settings")
public class WorkspaceSettingsJpaEntity {
    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "working_days", nullable = false)
    private String workingDays;

    @Column(name = "working_hours_start", nullable = false)
    private java.time.LocalTime workingHoursStart;

    @Column(name = "working_hours_end", nullable = false)
    private java.time.LocalTime workingHoursEnd;

    @Column(name = "at_risk_window_hours", nullable = false)
    private int atRiskWindowHours;

    @Column(name = "escalation_intervals_hours", nullable = false)
    private String escalationIntervalsHours;

    @Column(name = "quiet_hours_start", nullable = false)
    private java.time.LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end", nullable = false)
    private java.time.LocalTime quietHoursEnd;

    @Column(name = "invitation_approval_required", nullable = false)
    private boolean invitationApprovalRequired;

    @Column(name = "closure_coverage_threshold_percent", nullable = false)
    private int closureCoverageThresholdPercent;

    @Column(name = "template_idle_window_days", nullable = false)
    private int templateIdleWindowDays;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    protected WorkspaceSettingsJpaEntity() {}

    public WorkspaceSettingsJpaEntity(
            UUID id,
            UUID workspaceId,
            String timezone,
            String workingDays,
            java.time.LocalTime workingHoursStart,
            java.time.LocalTime workingHoursEnd,
            int atRiskWindowHours,
            String escalationIntervalsHours,
            java.time.LocalTime quietHoursStart,
            java.time.LocalTime quietHoursEnd,
            boolean invitationApprovalRequired,
            int closureCoverageThresholdPercent,
            int templateIdleWindowDays,
            Instant effectiveFrom,
            Instant effectiveTo) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.timezone = timezone;
        this.workingDays = workingDays;
        this.workingHoursStart = workingHoursStart;
        this.workingHoursEnd = workingHoursEnd;
        this.atRiskWindowHours = atRiskWindowHours;
        this.escalationIntervalsHours = escalationIntervalsHours;
        this.quietHoursStart = quietHoursStart;
        this.quietHoursEnd = quietHoursEnd;
        this.invitationApprovalRequired = invitationApprovalRequired;
        this.closureCoverageThresholdPercent = closureCoverageThresholdPercent;
        this.templateIdleWindowDays = templateIdleWindowDays;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public String getWorkingDays() {
        return workingDays;
    }

    public java.time.LocalTime getWorkingHoursStart() {
        return workingHoursStart;
    }

    public java.time.LocalTime getWorkingHoursEnd() {
        return workingHoursEnd;
    }

    public int getAtRiskWindowHours() {
        return atRiskWindowHours;
    }

    public String getEscalationIntervalsHours() {
        return escalationIntervalsHours;
    }

    public java.time.LocalTime getQuietHoursStart() {
        return quietHoursStart;
    }

    public java.time.LocalTime getQuietHoursEnd() {
        return quietHoursEnd;
    }

    public boolean isInvitationApprovalRequired() {
        return invitationApprovalRequired;
    }

    public int getClosureCoverageThresholdPercent() {
        return closureCoverageThresholdPercent;
    }

    public int getTemplateIdleWindowDays() {
        return templateIdleWindowDays;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getTimezone() {
        return timezone;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(Instant effectiveTo) {
        this.effectiveTo = effectiveTo;
    }
}
