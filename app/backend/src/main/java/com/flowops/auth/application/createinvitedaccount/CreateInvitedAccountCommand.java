package com.flowops.auth.application.createinvitedaccount;

import com.flowops.auth.application.shared.ClientContext;

public record CreateInvitedAccountCommand(
        String email, String displayName, String role, String password, ClientContext clientContext) {}
