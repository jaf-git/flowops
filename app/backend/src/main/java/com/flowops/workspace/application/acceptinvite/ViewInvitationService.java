package com.flowops.workspace.application.acceptinvite;

import com.flowops.workspace.application.shared.exception.InvitationNotUsableException;
import com.flowops.workspace.application.shared.port.ConsentCataloguePort;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.GenerateInvitationTokenPort;
import com.flowops.workspace.application.shared.port.LoadInvitationPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.domain.model.Invitation;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;
import com.flowops.workspace.domain.model.ReportingTree;
import com.flowops.workspace.domain.model.WorkspaceName;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewInvitationService implements ViewInvitationUseCase {
    private final LoadInvitationPort loadInvitationPort;
    private final LoadMembershipPort loadMembershipPort;
    private final DescribePeoplePort describePeoplePort;
    private final LoadWorkspacePort loadWorkspacePort;
    private final ConsentCataloguePort consentCataloguePort;
    private final GenerateInvitationTokenPort generateInvitationTokenPort;
    private final Clock clock;

    public ViewInvitationService(
            LoadInvitationPort loadInvitationPort,
            LoadMembershipPort loadMembershipPort,
            DescribePeoplePort describePeoplePort,
            LoadWorkspacePort loadWorkspacePort,
            ConsentCataloguePort consentCataloguePort,
            GenerateInvitationTokenPort generateInvitationTokenPort,
            Clock clock) {
        this.loadInvitationPort = loadInvitationPort;
        this.loadMembershipPort = loadMembershipPort;
        this.describePeoplePort = describePeoplePort;
        this.loadWorkspacePort = loadWorkspacePort;
        this.consentCataloguePort = consentCataloguePort;
        this.generateInvitationTokenPort = generateInvitationTokenPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ViewInvitationResult execute(ViewInvitationQuery query) {
        Instant now = clock.instant();
        Invitation invitation = loadInvitationPort
                .findByTokenHash(generateInvitationTokenPort.hash(query.token()))
                .orElseThrow(InvitationNotUsableException::new);

        boolean neverSent = invitation.state() != com.flowops.workspace.domain.enums.InvitationState.SENT;
        boolean pastItsDate = !invitation.expiresAt().isAfter(now);
        if (neverSent || pastItsDate) {
            throw new InvitationNotUsableException();
        }

        List<Membership> everybody = loadMembershipPort.listAll();

        Map<PersonId, DescribePeoplePort.PersonDescription> described = new HashMap<>();
        describePeoplePort
                .describe(everybody.stream().map(Membership::person).toList())
                .forEach(person -> described.put(person.id(), person));
        Map<PersonId, String> everybodysNames = new HashMap<>();
        Map<MembershipId, String> roles = new HashMap<>();
        for (Membership member : everybody) {
            DescribePeoplePort.PersonDescription person = described.get(member.person());
            everybodysNames.put(member.person(), person == null ? "" : person.displayName());
            roles.put(member.id(), person == null ? "" : person.role());
        }

        Membership manager = ReportingTree.of(everybody, roles)
                .firstActiveFrom(invitation.intendedManager())
                .orElseThrow(InvitationNotUsableException::new);
        boolean reassigned = !manager.id().equals(invitation.intendedManager());

        Membership inviter = everybody.stream()
                .filter(member -> member.id().equals(invitation.inviter()))
                .findFirst()
                .orElseThrow(InvitationNotUsableException::new);

        ConsentCataloguePort.ConsentText consent = consentCataloguePort.inLanguage(query.language());

        return new ViewInvitationResult(
                loadWorkspacePort.load().name().map(WorkspaceName::value).orElse(""),
                invitation.intendedRole(),
                everybodysNames.getOrDefault(manager.person(), ""),
                reassigned,
                everybodysNames.getOrDefault(inviter.person(), ""),
                consent.version(),
                consent.text());
    }
}
