package com.flowops.workspace.application.viewpeople;

import com.flowops.workspace.domain.enums.InvitationState;
import com.flowops.workspace.domain.enums.InvitedRole;
import com.flowops.workspace.domain.enums.MembershipStatus;
import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.InvitationId;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record ViewPeopleResult(List<Person> people, Optional<List<PendingInvitation>> invitations, boolean onlyMember) {
    public record Person(
            MembershipId membershipId,
            PersonId personId,
            String displayName,
            String role,
            Optional<MembershipId> manager,
            MembershipStatus status,
            Optional<Instant> deactivatedAt,
            boolean isSelf) {}

    public record PendingInvitation(
            InvitationId id,
            EmailAddress email,
            InvitedRole intendedRole,
            MembershipId intendedManager,
            MembershipId inviter,
            InvitationState state,
            Instant expiresAt) {}
}
