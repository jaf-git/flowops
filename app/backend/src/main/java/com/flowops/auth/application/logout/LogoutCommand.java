package com.flowops.auth.application.logout;

import com.flowops.auth.application.shared.ClientContext;

public record LogoutCommand(ClientContext clientContext) {}
