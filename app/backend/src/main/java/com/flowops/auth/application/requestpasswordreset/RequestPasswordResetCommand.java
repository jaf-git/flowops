package com.flowops.auth.application.requestpasswordreset;

import com.flowops.auth.application.shared.ClientContext;

public record RequestPasswordResetCommand(String email, ClientContext clientContext) {}
