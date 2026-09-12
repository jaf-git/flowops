package com.flowops.workspace.application.inviteperson;

import com.flowops.workspace.application.shared.exception.AlreadyMemberException;
import com.flowops.workspace.application.shared.exception.DeactivatedMemberException;
import com.flowops.workspace.application.shared.exception.DeclineWindowException;
import com.flowops.workspace.application.shared.exception.DuplicateInvitationException;
import com.flowops.workspace.application.shared.exception.ManagerInactiveException;
import com.flowops.workspace.application.shared.exception.ManagerNotEligibleForReportsException;
import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.exception.SelfInvitationException;
import com.flowops.workspace.application.shared.exception.SetupNotPermittedException;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.GenerateInvitationTokenPort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.InvitationLimitPort;
import com.flowops.workspace.application.shared.port.LoadInvitationPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.SaveInvitationPort;
import com.flowops.workspace.application.shared.port.SendInvitationPort;
import com.flowops.workspace.domain.enums.InvitationState;
import com.flowops.workspace.domain.enums.WorkspaceAction;
import com.flowops.workspace.domain.event.WorkspaceEvent;
import com.flowops.workspace.domain.model.Invitation;
import com.flowops.workspace.domain.model.InvitationId;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.WorkspaceId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitePersonService implements InvitePersonUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final DescribePeoplePort describePeoplePort;
    private final LoadWorkspacePort loadWorkspacePort;
    private final LoadMembershipPort loadMembershipPort;
    private final LoadInvitationPort loadInvitationPort;
    private final InvitationLimitPort invitationLimitPort;
    private final GenerateInvitationTokenPort generateInvitationTokenPort;
    private final SaveInvitationPort saveInvitationPort;
    private final AppendWorkspaceEventPort appendWorkspaceEventPort;
    private final SendInvitationPort sendInvitationPort;
    private final Duration invitationLifetime;
    private final Duration reconsiderationWindow;
    private final Clock clock;

    public InvitePersonService(
            IdentifyCallerPort identifyCallerPort,
            DescribePeoplePort describePeoplePort,
            LoadWorkspacePort loadWorkspacePort,
            LoadMembershipPort loadMembershipPort,
            LoadInvitationPort loadInvitationPort,
            InvitationLimitPort invitationLimitPort,
            GenerateInvitationTokenPort generateInvitationTokenPort,
            SaveInvitationPort saveInvitationPort,
            AppendWorkspaceEventPort appendWorkspaceEventPort,
            SendInvitationPort sendInvitationPort,
            @Value("${flowops.workspace.invitation.lifetime:P7D}") Duration invitationLifetime,
            @Value("${flowops.workspace.invitation.reconsideration-window:P30D}") Duration reconsiderationWindow,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.describePeoplePort = describePeoplePort;
        this.loadWorkspacePort = loadWorkspacePort;
        this.loadMembershipPort = loadMembershipPort;
        this.loadInvitationPort = loadInvitationPort;
        this.invitationLimitPort = invitationLimitPort;
        this.generateInvitationTokenPort = generateInvitationTokenPort;
        this.saveInvitationPort = saveInvitationPort;
        this.appendWorkspaceEventPort = appendWorkspaceEventPort;
        this.sendInvitationPort = sendInvitationPort;
        this.invitationLifetime = invitationLifetime;
        this.reconsiderationWindow = reconsiderationWindow;
        this.clock = clock;
    }

    @Override
    @Transactional
    public InvitePersonResult execute(InvitePersonCommand command) {
        Instant now = clock.instant();

        IdentifyCallerPort.Caller caller =
                identifyCallerPort.currentCaller().orElseThrow(NotAuthenticatedException::new);

        Membership inviter = loadMembershipPort.findByPerson(caller.id()).orElseThrow(SetupNotPermittedException::new);
        if (!inviter.isActive()) {
            throw new SetupNotPermittedException();
        }

        Membership intendedManager = loadMembershipPort
                .findById(command.intendedManager())
                .orElseThrow(() -> new ManagerInactiveException("that manager is no longer active"));
        if (!intendedManager.isActive()) {
            throw new ManagerInactiveException("that manager is no longer active");
        }

        String managerRole = describePeoplePort.describe(java.util.List.of(intendedManager.person())).stream()
                .findFirst()
                .map(DescribePeoplePort.PersonDescription::role)
                .orElse("");
        if (!"OWNER".equals(managerRole) && !"MANAGER".equals(managerRole)) {
            throw new ManagerNotEligibleForReportsException();
        }

        Optional<Membership> existing = loadMembershipPort.findByEmail(command.email());
        if (existing.isPresent()) {
            Membership member = existing.get();
            if (member.id().equals(inviter.id())) {
                throw new SelfInvitationException("you are already here");
            }
            if (member.isActive()) {
                throw new AlreadyMemberException("that person is already a member of this workspace");
            }
            throw new DeactivatedMemberException(
                    "that person is a deactivated member; reactivate them rather than inviting them again");
        }

        if (loadInvitationPort.findOpenFor(command.email()).isPresent()) {
            throw new DuplicateInvitationException(
                    "that address already has an open invitation; resend or revoke it instead");
        }

        Optional<Instant> declined = loadInvitationPort.findLastDeclineFor(command.email());
        if (declined.isPresent() && declined.get().isAfter(now.minus(reconsiderationWindow))) {
            throw new DeclineWindowException("that address declined recently and may be invited again after "
                    + declined.get().plus(reconsiderationWindow));
        }

        invitationLimitPort.check(inviter.id(), command.email());

        GenerateInvitationTokenPort.MintedToken minted = generateInvitationTokenPort.mint();
        WorkspaceId workspace = loadWorkspacePort.load().id();

        Invitation saved = saveInvitationPort.save(new Invitation(
                InvitationId.of(UUID.randomUUID()),
                workspace,
                command.email(),
                command.intendedRole(),
                intendedManager.id(),
                inviter.id(),
                InvitationState.SENT,
                minted.hash(),
                now.plus(invitationLifetime),
                now,
                null));

        appendWorkspaceEventPort.append(
                WorkspaceEvent.byActor(WorkspaceAction.INVITATION_CREATED, caller.id(), workspace, now));

        sendInvitationPort.sendInvitation(saved.email(), minted.token());

        return new InvitePersonResult(
                saved.id(),
                saved.email(),
                saved.intendedRole(),
                saved.intendedManager(),
                saved.state(),
                saved.expiresAt());
    }
}
