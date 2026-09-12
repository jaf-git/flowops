package com.flowops.workspace.domain.model;

import com.flowops.workspace.domain.enums.InvitationState;
import com.flowops.workspace.domain.enums.InvitedRole;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record Invitation(
        InvitationId id,
        WorkspaceId workspace,
        EmailAddress email,
        InvitedRole intendedRole,
        MembershipId intendedManager,
        MembershipId inviter,
        InvitationState state,
        String tokenHash,
        Instant expiresAt,
        Instant createdAt,
        Instant declinedAt) {
    public Invitation {
        Objects.requireNonNull(id, "an invitation identity is required");
        Objects.requireNonNull(workspace, "an invitation belongs to a workspace");
        Objects.requireNonNull(email, "an invitation names an address");
        Objects.requireNonNull(intendedRole, "an invitation carries an intended role");
        Objects.requireNonNull(intendedManager, "an invitation names the manager the person will report to");
        Objects.requireNonNull(inviter, "an invitation records who sent it");
        Objects.requireNonNull(state, "an invitation has a state");
        Objects.requireNonNull(tokenHash, "an invitation carries a token");
        Objects.requireNonNull(expiresAt, "an invitation expires");
        Objects.requireNonNull(createdAt, "an invitation records when it was created");
    }

    public Optional<Instant> declinedWhen() {
        return Optional.ofNullable(declinedAt);
    }

    public boolean isOpen() {
        return state.isOpen();
    }

    public boolean isTerminal() {
        return !state.isOpen();
    }

    public boolean isAccepted() {
        return state == InvitationState.ACCEPTED;
    }

    public Invitation revoke() {
        if (isTerminal()) {
            throw new IllegalStateException("an invitation in state " + state + " cannot be revoked again");
        }
        return new Invitation(
                id,
                workspace,
                email,
                intendedRole,
                intendedManager,
                inviter,
                InvitationState.REVOKED,
                tokenHash,
                expiresAt,
                createdAt,
                declinedAt);
    }

    public boolean isUsableAt(Instant now) {
        return state == InvitationState.SENT && now.isBefore(expiresAt);
    }

    public Invitation acceptAt(Instant now) {
        refuseUnlessRedeemableAt(now, "accepted");
        return new Invitation(
                id,
                workspace,
                email,
                intendedRole,
                intendedManager,
                inviter,
                InvitationState.ACCEPTED,
                tokenHash,
                expiresAt,
                createdAt,
                declinedAt);
    }

    public Invitation declineAt(Instant now) {
        refuseUnlessRedeemableAt(now, "declined");
        return new Invitation(
                id,
                workspace,
                email,
                intendedRole,
                intendedManager,
                inviter,
                InvitationState.DECLINED,
                tokenHash,
                expiresAt,
                createdAt,
                now);
    }

    private void refuseUnlessRedeemableAt(Instant now, String attempted) {
        if (state != InvitationState.SENT) {
            throw new IllegalStateException("an invitation in state " + state + " cannot be " + attempted);
        }
        if (!now.isBefore(expiresAt)) {
            throw new IllegalStateException("an invitation that expired at " + expiresAt + " cannot be " + attempted);
        }
    }

    public boolean wasCreatedBy(MembershipId membership) {
        return inviter.equals(membership);
    }
}
