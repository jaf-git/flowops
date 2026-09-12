package com.flowops.workspace.infrastructure.persistence.mapper;

import com.flowops.workspace.domain.enums.WorkspaceAction;
import com.flowops.workspace.domain.enums.WorkspaceUse;
import com.flowops.workspace.domain.event.WorkspaceEvent;
import com.flowops.workspace.domain.model.PersonId;
import com.flowops.workspace.domain.model.Timezone;
import com.flowops.workspace.domain.model.Workspace;
import com.flowops.workspace.domain.model.WorkspaceId;
import com.flowops.workspace.domain.model.WorkspaceName;
import com.flowops.workspace.domain.model.WorkspaceSettings;
import com.flowops.workspace.domain.model.WorkspaceSettingsId;
import com.flowops.workspace.infrastructure.persistence.entity.WorkspaceEventJpaEntity;
import com.flowops.workspace.infrastructure.persistence.entity.WorkspaceJpaEntity;
import com.flowops.workspace.infrastructure.persistence.entity.WorkspaceSettingsJpaEntity;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceEntityMapper {
    public Workspace toDomain(WorkspaceJpaEntity entity) {
        return Workspace.rebuild(
                WorkspaceId.of(entity.getId()),
                entity.getName() == null ? null : new WorkspaceName(entity.getName()),
                entity.getWorkspaceUse() == null ? null : WorkspaceUse.valueOf(entity.getWorkspaceUse()),
                entity.getCreatedAt());
    }

    public WorkspaceSettingsJpaEntity toEntity(WorkspaceSettings settings) {
        return new WorkspaceSettingsJpaEntity(
                settings.id().value(),
                settings.workspaceId().value(),
                settings.timezone().value(),
                settings.workingWeek().mask(),
                settings.workingWeek().start(),
                settings.workingWeek().end(),
                settings.atRiskWindowHours(),
                settings.escalationLadder().stored(),
                settings.quietHours().start(),
                settings.quietHours().end(),
                settings.invitationApprovalRequired(),
                settings.analysisThresholds().closureCoverageThresholdPercent(),
                settings.analysisThresholds().templateIdleWindowDays(),
                settings.effectiveFrom(),
                settings.effectiveTo().orElse(null));
    }

    public WorkspaceSettings toDomain(WorkspaceSettingsJpaEntity entity) {
        return WorkspaceSettings.rebuild(
                WorkspaceSettingsId.of(entity.getId()),
                WorkspaceId.of(entity.getWorkspaceId()),
                new Timezone(entity.getTimezone()),
                com.flowops.workspace.domain.model.WorkingWeek.fromMask(
                        entity.getWorkingDays(), entity.getWorkingHoursStart(), entity.getWorkingHoursEnd()),
                entity.getAtRiskWindowHours(),
                com.flowops.workspace.domain.model.EscalationLadder.parse(entity.getEscalationIntervalsHours()),
                new com.flowops.workspace.domain.model.QuietHours(
                        entity.getQuietHoursStart(), entity.getQuietHoursEnd()),
                entity.isInvitationApprovalRequired(),
                new com.flowops.workspace.domain.model.AnalysisThresholds(
                        entity.getClosureCoverageThresholdPercent(), entity.getTemplateIdleWindowDays()),
                entity.getEffectiveFrom(),
                entity.getEffectiveTo());
    }

    public WorkspaceEventJpaEntity toEntity(WorkspaceEvent event) {
        return new WorkspaceEventJpaEntity(
                event.id(),
                event.action().name(),
                identifierOf(event.actor()),
                identifierOf(event.subject()),
                identifierOf(event.formerManager()),
                identifierOf(event.newManager()),
                event.workspaceId().value(),
                event.occurredAt());
    }

    public WorkspaceEvent toDomain(WorkspaceEventJpaEntity entity) {
        return new WorkspaceEvent(
                entity.getId(),
                WorkspaceAction.valueOf(entity.getAction()),
                personOf(entity.getActorUserId()),
                personOf(entity.getSubjectUserId()),
                personOf(entity.getFormerManagerUserId()),
                personOf(entity.getNewManagerUserId()),
                WorkspaceId.of(entity.getWorkspaceId()),
                entity.getOccurredAt());
    }

    private static UUID identifierOf(PersonId person) {
        return person == null ? null : person.value();
    }

    private static PersonId personOf(UUID identifier) {
        return identifier == null ? null : PersonId.of(identifier);
    }
}
