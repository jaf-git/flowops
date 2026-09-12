package com.flowops.workspace.application.revokeinvitation;

import com.flowops.workspace.application.shared.exception.InvitationAlreadyAcceptedException;
import com.flowops.workspace.application.shared.exception.InvitationNotFoundException;
import com.flowops.workspace.application.shared.exception.InvitationNotYoursException;
import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.exception.SetupNotPermittedException;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import com.flowops.workspace.application.shared.port.CallerPermissionsPort;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.LoadInvitationPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.SaveInvitationPort;
import com.flowops.workspace.domain.enums.WorkspaceAction;
import com.flowops.workspace.domain.event.WorkspaceEvent;
import com.flowops.workspace.domain.model.Invitation;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.WorkspaceId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevokeInvitationService implements RevokeInvitationUseCase {
    private static final String REVOKE_ANY = "INVITATION_REVOKE_ANY";

    private final IdentifyCallerPort identifyCallerPort;
    private final LoadMembershipPort loadMembershipPort;
    private final LoadInvitationPort loadInvitationPort;
    private final SaveInvitationPort saveInvitationPort;
    private final AppendWorkspaceEventPort appendWorkspaceEventPort;
    private final LoadWorkspacePort loadWorkspacePort;
    private final DescribePeoplePort describePeoplePort;
    private final CallerPermissionsPort callerPermissionsPort;
    private final Clock clock;

    public RevokeInvitationService(
            IdentifyCallerPort identifyCallerPort,
            LoadMembershipPort loadMembershipPort,
            LoadInvitationPort loadInvitationPort,
            SaveInvitationPort saveInvitationPort,
            AppendWorkspaceEventPort appendWorkspaceEventPort,
            LoadWorkspacePort loadWorkspacePort,
            DescribePeoplePort describePeoplePort,
            CallerPermissionsPort callerPermissionsPort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadMembershipPort = loadMembershipPort;
        this.loadInvitationPort = loadInvitationPort;
        this.saveInvitationPort = saveInvitationPort;
        this.appendWorkspaceEventPort = appendWorkspaceEventPort;
        this.loadWorkspacePort = loadWorkspacePort;
        this.describePeoplePort = describePeoplePort;
        this.callerPermissionsPort = callerPermissionsPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RevokeInvitationResult execute(RevokeInvitationCommand command) {
        Instant now = clock.instant();

        IdentifyCallerPort.Caller caller =
                identifyCallerPort.currentCaller().orElseThrow(NotAuthenticatedException::new);
        Membership actor = loadMembershipPort.findByPerson(caller.id()).orElseThrow(SetupNotPermittedException::new);

        Invitation invitation = loadInvitationPort
                .findByIdForUpdate(command.invitation())
                .orElseThrow(InvitationNotFoundException::new);

        if (!callerPermissionsPort.callerHolds(REVOKE_ANY) && !invitation.wasCreatedBy(actor.id())) {
            throw new InvitationNotYoursException();
        }

        if (invitation.isAccepted()) {
            throw new InvitationAlreadyAcceptedException(whoAccepted(invitation));
        }

        if (invitation.isTerminal()) {
            return new RevokeInvitationResult(invitation.id(), invitation.email(), invitation.state(), null);
        }

        Invitation revoked = saveInvitationPort.save(invitation.revoke());

        WorkspaceId workspace = loadWorkspacePort.load().id();
        appendWorkspaceEventPort.append(
                WorkspaceEvent.byActor(WorkspaceAction.INVITATION_REVOKED, caller.id(), workspace, now));

        return new RevokeInvitationResult(revoked.id(), revoked.email(), revoked.state(), now);
    }

    private String whoAccepted(Invitation invitation) {
        return loadMembershipPort
                .findByEmail(invitation.email())
                .flatMap(member -> describePeoplePort.describe(List.of(member.person())).stream()
                        .findFirst()
                        .map(DescribePeoplePort.PersonDescription::displayName))
                .orElse("");
    }
}
