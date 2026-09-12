package com.flowops.workspace.application.revokeinvitation;

import com.flowops.workspace.domain.model.InvitationId;

public record RevokeInvitationCommand(InvitationId invitation) {}
