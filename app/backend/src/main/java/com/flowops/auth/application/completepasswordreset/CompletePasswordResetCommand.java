package com.flowops.auth.application.completepasswordreset;

import com.flowops.auth.application.shared.ClientContext;

public record CompletePasswordResetCommand(String clearToken, String newPassword, ClientContext clientContext) {}
