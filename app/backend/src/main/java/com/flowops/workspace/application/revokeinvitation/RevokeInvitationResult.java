package com.flowops.workspace.application.revokeinvitation;

import com.flowops.workspace.domain.enums.InvitationState;
import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.InvitationId;
import java.time.Instant;

public record RevokeInvitationResult(InvitationId id, EmailAddress email, InvitationState state, Instant revokedAt) {}
