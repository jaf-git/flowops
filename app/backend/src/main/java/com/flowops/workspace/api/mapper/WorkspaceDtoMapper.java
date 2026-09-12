package com.flowops.workspace.api.mapper;

import com.flowops.workspace.api.dto.PeopleResponse;
import com.flowops.workspace.api.dto.WorkspaceResponse;
import com.flowops.workspace.api.dto.WorkspaceSetupPrefillResponse;
import com.flowops.workspace.api.dto.WorkspaceSetupResponse;
import com.flowops.workspace.application.setupworkspace.SetupWorkspaceResult;
import com.flowops.workspace.application.viewpeople.ViewPeopleResult;
import com.flowops.workspace.application.viewsetupprefill.ViewSetupPrefillResult;
import com.flowops.workspace.domain.model.MembershipId;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceDtoMapper {
    public WorkspaceSetupResponse toResponse(SetupWorkspaceResult result) {
        return new WorkspaceSetupResponse(
                new WorkspaceResponse(
                        result.workspaceId().value(),
                        result.name().value(),
                        result.use(),
                        result.timezone().value()),
                result.landingTarget());
    }

    public WorkspaceSetupPrefillResponse toResponse(ViewSetupPrefillResult result) {
        return new WorkspaceSetupPrefillResponse(
                result.setupCompleted(), result.suggestedTimezone(), result.availableTimezones());
    }

    public PeopleResponse toResponse(ViewPeopleResult result) {
        return new PeopleResponse(
                result.people().stream().map(WorkspaceDtoMapper::toResponse).toList(),
                result.invitations()
                        .map(invitations -> invitations.stream()
                                .map(WorkspaceDtoMapper::toResponse)
                                .toList())
                        .orElse(null),
                result.onlyMember());
    }

    private static PeopleResponse.PersonResponse toResponse(ViewPeopleResult.Person person) {
        return new PeopleResponse.PersonResponse(
                person.membershipId().value(),
                person.personId().value(),
                person.displayName(),
                person.role(),
                person.manager().map(MembershipId::value).orElse(null),
                person.status().name(),
                person.deactivatedAt().orElse(null),
                person.isSelf());
    }

    private static PeopleResponse.PendingInvitationResponse toResponse(ViewPeopleResult.PendingInvitation invitation) {
        return new PeopleResponse.PendingInvitationResponse(
                invitation.id().value(),
                invitation.email().value(),
                invitation.intendedRole().name(),
                invitation.intendedManager().value(),
                invitation.inviter().value(),
                invitation.state().name(),
                invitation.expiresAt());
    }
}
