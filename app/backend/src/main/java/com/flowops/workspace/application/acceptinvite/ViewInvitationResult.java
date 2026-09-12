package com.flowops.workspace.application.acceptinvite;

import com.flowops.workspace.domain.enums.InvitedRole;

public record ViewInvitationResult(
        String workspaceName,
        InvitedRole role,
        String managerName,
        boolean managerReassigned,
        String inviterName,
        String consentVersion,
        String consentText) {}
