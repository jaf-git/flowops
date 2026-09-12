package com.flowops.workspace.application.acceptinvite;

import com.flowops.workspace.application.shared.exception.ConsentNotGivenException;
import com.flowops.workspace.application.shared.exception.ConsentVersionStaleException;
import com.flowops.workspace.application.shared.exception.InvitationNotUsableException;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import com.flowops.workspace.application.shared.port.ConsentCataloguePort;
import com.flowops.workspace.application.shared.port.CreateInvitedAccountPort;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.GenerateInvitationTokenPort;
import com.flowops.workspace.application.shared.port.LoadInvitationPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.SaveConsentRecordPort;
import com.flowops.workspace.application.shared.port.SaveInvitationPort;
import com.flowops.workspace.application.shared.port.SaveMembershipPort;
import com.flowops.workspace.domain.enums.WorkspaceAction;
import com.flowops.workspace.domain.event.WorkspaceEvent;
import com.flowops.workspace.domain.model.ConsentRecord;
import com.flowops.workspace.domain.model.ConsentRecordId;
import com.flowops.workspace.domain.model.Invitation;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;
import com.flowops.workspace.domain.model.ReportingTree;
import com.flowops.workspace.domain.model.Workspace;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcceptInvitationService implements AcceptInvitationUseCase {
    private final LoadInvitationPort loadInvitationPort;
    private final SaveInvitationPort saveInvitationPort;
    private final LoadMembershipPort loadMembershipPort;
    private final SaveMembershipPort saveMembershipPort;
    private final DescribePeoplePort describePeoplePort;
    private final LoadWorkspacePort loadWorkspacePort;
    private final ConsentCataloguePort consentCataloguePort;
    private final SaveConsentRecordPort saveConsentRecordPort;
    private final CreateInvitedAccountPort createInvitedAccountPort;
    private final GenerateInvitationTokenPort generateInvitationTokenPort;
    private final AppendWorkspaceEventPort appendWorkspaceEventPort;
    private final Clock clock;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public AcceptInvitationService(
            LoadInvitationPort loadInvitationPort,
            SaveInvitationPort saveInvitationPort,
            LoadMembershipPort loadMembershipPort,
            SaveMembershipPort saveMembershipPort,
            DescribePeoplePort describePeoplePort,
            LoadWorkspacePort loadWorkspacePort,
            ConsentCataloguePort consentCataloguePort,
            SaveConsentRecordPort saveConsentRecordPort,
            CreateInvitedAccountPort createInvitedAccountPort,
            GenerateInvitationTokenPort generateInvitationTokenPort,
            AppendWorkspaceEventPort appendWorkspaceEventPort,
            Clock clock) {
        this.loadInvitationPort = loadInvitationPort;
        this.saveInvitationPort = saveInvitationPort;
        this.loadMembershipPort = loadMembershipPort;
        this.saveMembershipPort = saveMembershipPort;
        this.describePeoplePort = describePeoplePort;
        this.loadWorkspacePort = loadWorkspacePort;
        this.consentCataloguePort = consentCataloguePort;
        this.saveConsentRecordPort = saveConsentRecordPort;
        this.createInvitedAccountPort = createInvitedAccountPort;
        this.generateInvitationTokenPort = generateInvitationTokenPort;
        this.appendWorkspaceEventPort = appendWorkspaceEventPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AcceptInvitationResult execute(AcceptInvitationCommand command) {
        Instant now = clock.instant();

        Invitation invitation = loadInvitationPort
                .findByTokenHashForUpdate(generateInvitationTokenPort.hash(command.token()))
                .orElseThrow(InvitationNotUsableException::new);
        if (!invitation.isUsableAt(now)) {
            throw new InvitationNotUsableException();
        }

        if (!command.consentAccepted()) {
            throw new ConsentNotGivenException();
        }

        ConsentCataloguePort.ConsentText consent = consentCataloguePort.inLanguage(command.language());
        if (!consent.version().equals(command.consentVersion())) {
            throw new ConsentVersionStaleException();
        }

        Membership manager = managerFor(invitation);
        Workspace workspace = loadWorkspacePort.load();

        CreateInvitedAccountPort.InvitedAccount account = createInvitedAccountPort.create(
                invitation.email(),
                command.displayName(),
                invitation.intendedRole().name(),
                command.password());

        saveMembershipPort.save(
                workspace.id(),
                new Membership(
                        MembershipId.of(UUID.randomUUID()),
                        account.person(),
                        com.flowops.workspace.domain.enums.MembershipStatus.ACTIVE,
                        manager.id()));

        saveConsentRecordPort.save(new ConsentRecord(
                ConsentRecordId.generate(),
                account.person(),
                consent.language(),
                consent.version(),
                consent.text(),
                now));

        saveInvitationPort.save(invitation.acceptAt(now));

        appendWorkspaceEventPort.append(
                WorkspaceEvent.byActor(WorkspaceAction.INVITATION_ACCEPTED, account.person(), workspace.id(), now));

        return new AcceptInvitationResult(
                account.person().value(),
                account.email(),
                account.accountState(),
                account.permissions(),
                account.landingTarget());
    }

    private Membership managerFor(Invitation invitation) {
        List<Membership> everybody = loadMembershipPort.listAll();
        Map<MembershipId, String> roles = new HashMap<>();
        Map<PersonId, DescribePeoplePort.PersonDescription> described = new HashMap<>();
        describePeoplePort
                .describe(everybody.stream().map(Membership::person).toList())
                .forEach(person -> described.put(person.id(), person));
        for (Membership member : everybody) {
            DescribePeoplePort.PersonDescription person = described.get(member.person());
            roles.put(member.id(), person == null ? "" : person.role());
        }
        return ReportingTree.of(everybody, roles)
                .firstActiveFrom(invitation.intendedManager())
                .orElseThrow(InvitationNotUsableException::new);
    }
}
