package com.flowops.workspace.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.workspace.domain.enums.MembershipStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("AUTH-ACCEPT-INVITE-01")
class ReportingTreeActiveAncestorTest {
    private static final MembershipId MARIA = MembershipId.of(UUID.randomUUID());
    private static final MembershipId IONUT = MembershipId.of(UUID.randomUUID());
    private static final MembershipId IOANA = MembershipId.of(UUID.randomUUID());

    @Test
    void anActiveManagerIsTheirOwnAnswer() {
        ReportingTree tree = tree(MembershipStatus.ACTIVE, MembershipStatus.ACTIVE);

        assertThat(tree.firstActiveFrom(IOANA).map(Membership::id)).contains(IOANA);
    }

    @Test
    void aDeactivatedManagerHandsTheInvitationToTheirOwnManager() {
        ReportingTree tree = tree(MembershipStatus.ACTIVE, MembershipStatus.DEACTIVATED);

        assertThat(tree.firstActiveFrom(IOANA).map(Membership::id))
                .as("Ionuț, who is above Ioana — not Maria, who is merely the owner")
                .contains(IONUT);
    }

    @Test
    void theWalkContinuesPastASecondDeactivatedManager() {
        ReportingTree tree = tree(MembershipStatus.DEACTIVATED, MembershipStatus.DEACTIVATED);

        assertThat(tree.firstActiveFrom(IOANA).map(Membership::id))
                .as("both Ioana and Ionuț have gone, so the line reaches Maria")
                .contains(MARIA);
    }

    @Test
    void somebodyWhoIsNotInTheTreeHasNoAncestor() {
        ReportingTree tree = tree(MembershipStatus.ACTIVE, MembershipStatus.ACTIVE);

        assertThat(tree.firstActiveFrom(MembershipId.of(UUID.randomUUID()))).isEmpty();
    }

    private static ReportingTree tree(MembershipStatus ionut, MembershipStatus ioana) {
        return ReportingTree.of(
                List.of(
                        membership(MARIA, null, MembershipStatus.ACTIVE),
                        membership(IONUT, MARIA, ionut),
                        membership(IOANA, IONUT, ioana)),
                Map.of(MARIA, "OWNER", IONUT, "MANAGER", IOANA, "MANAGER"));
    }

    private static Membership membership(MembershipId id, MembershipId manager, MembershipStatus status) {
        return new Membership(id, PersonId.of(UUID.randomUUID()), status, manager, null);
    }
}
