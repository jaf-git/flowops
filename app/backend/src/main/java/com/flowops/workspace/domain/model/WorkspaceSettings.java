package com.flowops.workspace.domain.model;

import com.flowops.workspace.domain.exception.AtRiskWindowInvalidException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class WorkspaceSettings {
    private final WorkspaceSettingsId id;
    private final WorkspaceId workspaceId;
    private final Timezone timezone;
    private final WorkingWeek workingWeek;
    private final int atRiskWindowHours;
    private final EscalationLadder escalationLadder;
    private final QuietHours quietHours;
    private final boolean invitationApprovalRequired;
    private final AnalysisThresholds analysisThresholds;
    private final Instant effectiveFrom;
    private final Instant effectiveTo;

    private WorkspaceSettings(
            WorkspaceSettingsId id,
            WorkspaceId workspaceId,
            Timezone timezone,
            WorkingWeek workingWeek,
            int atRiskWindowHours,
            EscalationLadder escalationLadder,
            QuietHours quietHours,
            boolean invitationApprovalRequired,
            AnalysisThresholds analysisThresholds,
            Instant effectiveFrom,
            Instant effectiveTo) {
        this.id = Objects.requireNonNull(id);
        this.workspaceId = Objects.requireNonNull(workspaceId);
        this.timezone = Objects.requireNonNull(timezone);
        this.workingWeek = Objects.requireNonNull(workingWeek);
        this.escalationLadder = Objects.requireNonNull(escalationLadder);
        this.quietHours = Objects.requireNonNull(quietHours);
        if (atRiskWindowHours <= 0) {
            throw new AtRiskWindowInvalidException();
        }
        this.atRiskWindowHours = atRiskWindowHours;
        this.invitationApprovalRequired = invitationApprovalRequired;
        this.analysisThresholds = Objects.requireNonNull(analysisThresholds);
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom);
        this.effectiveTo = effectiveTo;
    }

    public static WorkspaceSettings shippingDefaults(WorkspaceId workspaceId, Timezone timezone, Instant from) {
        return new WorkspaceSettings(
                WorkspaceSettingsId.generate(),
                workspaceId,
                timezone,
                WorkingWeek.fromMask("YYYYYNN", java.time.LocalTime.of(9, 0), java.time.LocalTime.of(17, 0)),
                24,
                EscalationLadder.parse("24,72,168"),
                new QuietHours(java.time.LocalTime.of(22, 0), java.time.LocalTime.of(6, 0)),
                false,
                AnalysisThresholds.shippingDefaults(),
                from,
                null);
    }

    public static WorkspaceSettings inForceFrom(WorkspaceId workspaceId, Timezone timezone, Instant from) {
        return shippingDefaults(workspaceId, timezone, from);
    }

    public static WorkspaceSettings rebuild(
            WorkspaceSettingsId id,
            WorkspaceId workspaceId,
            Timezone timezone,
            WorkingWeek workingWeek,
            int atRiskWindowHours,
            EscalationLadder escalationLadder,
            QuietHours quietHours,
            boolean invitationApprovalRequired,
            AnalysisThresholds analysisThresholds,
            Instant effectiveFrom,
            Instant effectiveTo) {
        return new WorkspaceSettings(
                id,
                workspaceId,
                timezone,
                workingWeek,
                atRiskWindowHours,
                escalationLadder,
                quietHours,
                invitationApprovalRequired,
                analysisThresholds,
                effectiveFrom,
                effectiveTo);
    }

    public WorkspaceSettings supersededBy(
            Timezone newTimezone,
            WorkingWeek newWorkingWeek,
            int newAtRiskWindowHours,
            EscalationLadder newLadder,
            QuietHours newQuietHours,
            boolean newApprovalRequired,
            AnalysisThresholds newThresholds,
            Instant from) {
        return new WorkspaceSettings(
                WorkspaceSettingsId.generate(),
                workspaceId,
                newTimezone,
                newWorkingWeek,
                newAtRiskWindowHours,
                newLadder,
                newQuietHours,
                newApprovalRequired,
                newThresholds,
                from,
                null);
    }

    public WorkspaceSettingsId id() {
        return id;
    }

    public WorkspaceId workspaceId() {
        return workspaceId;
    }

    public Timezone timezone() {
        return timezone;
    }

    public WorkingWeek workingWeek() {
        return workingWeek;
    }

    public int atRiskWindowHours() {
        return atRiskWindowHours;
    }

    public EscalationLadder escalationLadder() {
        return escalationLadder;
    }

    public QuietHours quietHours() {
        return quietHours;
    }

    public boolean invitationApprovalRequired() {
        return invitationApprovalRequired;
    }

    public AnalysisThresholds analysisThresholds() {
        return analysisThresholds;
    }

    public Instant effectiveFrom() {
        return effectiveFrom;
    }

    public Optional<Instant> effectiveTo() {
        return Optional.ofNullable(effectiveTo);
    }
}
