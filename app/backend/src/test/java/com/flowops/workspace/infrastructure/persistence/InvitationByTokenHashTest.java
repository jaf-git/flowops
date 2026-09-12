package com.flowops.workspace.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.workspace.WorkspaceIntegrationTest;
import com.flowops.workspace.application.shared.port.GenerateInvitationTokenPort;
import com.flowops.workspace.application.shared.port.LoadInvitationPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.SaveInvitationPort;
import com.flowops.workspace.domain.enums.InvitationState;
import com.flowops.workspace.domain.enums.InvitedRole;
import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.Invitation;
import com.flowops.workspace.domain.model.InvitationId;
import com.flowops.workspace.domain.model.MembershipId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("AUTH-ACCEPT-INVITE-01")
class InvitationByTokenHashTest extends WorkspaceIntegrationTest {
    @Autowired
    private SaveInvitationPort saveInvitation;

    @Autowired
    private LoadInvitationPort loadInvitation;

    @Autowired
    private LoadWorkspacePort loadWorkspace;

    @Autowired
    private GenerateInvitationTokenPort tokens;

    @Autowired
    private LoadMembershipPort loadMembership;

    @Test
    void anInvitationIsFoundByTheHashOfTheTokenThatWasSentWithIt() throws Exception {
        GenerateInvitationTokenPort.MintedToken minted = tokens.mint();
        InvitationId saved = anInvitationStoredWith(minted.hash());

        Optional<Invitation> found =
                loadInvitation.findByTokenHash(tokens.hash(minted.token().value()));

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().id()).isEqualTo(saved);
    }

    @Test
    void aTokenThatMatchesNothingFindsNothing() throws Exception {
        anInvitationStoredWith(tokens.mint().hash());

        assertThat(loadInvitation.findByTokenHash(
                        tokens.hash(tokens.mint().token().value())))
                .isEmpty();
    }

    private InvitationId anInvitationStoredWith(String tokenHash) throws Exception {
        setUpTheWorkspace(anOwnerSignedIn());
        Instant now = Instant.now();
        MembershipId owner = loadMembership.listAll().getFirst().id();

        Invitation invitation = new Invitation(
                InvitationId.of(UUID.randomUUID()),
                loadWorkspace.load().id(),
                EmailAddress.of("cosmin@atelier.ro"),
                InvitedRole.EMPLOYEE,
                owner,
                owner,
                InvitationState.SENT,
                tokenHash,
                now.plus(7, ChronoUnit.DAYS),
                now,
                null);
        return saveInvitation.save(invitation).id();
    }
}
