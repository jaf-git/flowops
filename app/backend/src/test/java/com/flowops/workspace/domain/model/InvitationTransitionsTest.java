package com.flowops.workspace.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.workspace.domain.enums.InvitationState;
import com.flowops.workspace.domain.enums.InvitedRole;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("AUTH-ACCEPT-INVITE-01")
class InvitationTransitionsTest {
    private static final Instant NOW = Instant.parse("2026-08-06T09:00:00Z");
    private static final Instant NEXT_WEEK = NOW.plus(7, ChronoUnit.DAYS);

    @Test
    void acceptingASentInvitationChangesTheStateAndNothingElse() {
        Invitation sent = invitation(InvitationState.SENT, NEXT_WEEK, null);

        Invitation accepted = sent.acceptAt(NOW);

        assertThat(accepted.state()).isEqualTo(InvitationState.ACCEPTED);
        assertThat(accepted.isAccepted()).isTrue();
        assertThat(accepted)
                .usingRecursiveComparison()
                .ignoringFields("state")
                .as("acceptance decides one field; rewriting any other would lose what was agreed to")
                .isEqualTo(sent);
    }

    @Test
    void acceptingLeavesTheDeclinedMomentUnset() {
        Invitation accepted = invitation(InvitationState.SENT, NEXT_WEEK, null).acceptAt(NOW);

        assertThat(accepted.declinedWhen()).isEmpty();
    }

    @Test
    void anInvitationAwaitingApprovalCannotBeAccepted() {
        Invitation awaiting = invitation(InvitationState.AWAITING_APPROVAL, NEXT_WEEK, null);

        assertThatThrownBy(() -> awaiting.acceptAt(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AWAITING_APPROVAL");
    }

    @Test
    void anAcceptedInvitationCannotBeAcceptedTwice() {
        Invitation accepted = invitation(InvitationState.ACCEPTED, NEXT_WEEK, null);

        assertThatThrownBy(() -> accepted.acceptAt(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aRevokedInvitationCannotBeAccepted() {
        Invitation revoked = invitation(InvitationState.REVOKED, NEXT_WEEK, null);

        assertThatThrownBy(() -> revoked.acceptAt(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anExpiredInvitationCannotBeAcceptedEvenThoughItIsStillMarkedSent() {
        Invitation expired = invitation(InvitationState.SENT, NOW.minus(1, ChronoUnit.SECONDS), null);

        assertThatThrownBy(() -> expired.acceptAt(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void anInvitationIsUsableRightUpToTheMomentItExpires() {
        Invitation onTheEdge = invitation(InvitationState.SENT, NOW, null);

        assertThat(onTheEdge.isUsableAt(NOW.minus(1, ChronoUnit.SECONDS))).isTrue();
        assertThat(onTheEdge.isUsableAt(NOW)).isFalse();
    }

    @Test
    void decliningRecordsTheMomentTheWindowIsMeasuredFrom() {
        Invitation sent = invitation(InvitationState.SENT, NEXT_WEEK, null);

        Invitation declined = sent.declineAt(NOW);

        assertThat(declined.state()).isEqualTo(InvitationState.DECLINED);
        assertThat(declined.declinedWhen()).contains(NOW);
    }

    @Test
    void decliningChangesOnlyTheStateAndTheMoment() {
        Invitation sent = invitation(InvitationState.SENT, NEXT_WEEK, null);

        Invitation declined = sent.declineAt(NOW);

        assertThat(declined)
                .usingRecursiveComparison()
                .ignoringFields("state", "declinedAt")
                .isEqualTo(sent);
    }

    @Test
    void anInvitationAwaitingApprovalCannotBeDeclined() {
        Invitation awaiting = invitation(InvitationState.AWAITING_APPROVAL, NEXT_WEEK, null);

        assertThatThrownBy(() -> awaiting.declineAt(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aDeclinedInvitationCannotBeDeclinedTwice() {
        Invitation declined = invitation(InvitationState.DECLINED, NEXT_WEEK, NOW);

        assertThatThrownBy(() -> declined.declineAt(NOW.plusSeconds(60))).isInstanceOf(IllegalStateException.class);
    }

    private static Invitation invitation(InvitationState state, Instant expiresAt, Instant declinedAt) {
        return new Invitation(
                InvitationId.of(UUID.randomUUID()),
                WorkspaceId.of(UUID.randomUUID()),
                EmailAddress.of("cosmin.ionescu@atelier.ro"),
                InvitedRole.EMPLOYEE,
                MembershipId.of(UUID.randomUUID()),
                MembershipId.of(UUID.randomUUID()),
                state,
                "a-hash-of-a-token",
                expiresAt,
                NOW.minus(1, ChronoUnit.DAYS),
                declinedAt);
    }
}
