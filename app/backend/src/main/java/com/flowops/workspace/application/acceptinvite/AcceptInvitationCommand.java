package com.flowops.workspace.application.acceptinvite;

public record AcceptInvitationCommand(
        String token,
        String displayName,
        String password,
        boolean consentAccepted,
        String consentVersion,
        String language) {}
