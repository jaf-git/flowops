package com.flowops.workspace.application.inviteperson;

import com.flowops.workspace.domain.enums.InvitationState;
import com.flowops.workspace.domain.enums.InvitedRole;
import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.InvitationId;
import com.flowops.workspace.domain.model.MembershipId;
import java.time.Instant;

public record InvitePersonResult(
        InvitationId id,
        EmailAddress email,
        InvitedRole intendedRole,
        MembershipId intendedManager,
        InvitationState state,
        Instant expiresAt) {}
