package com.flowops.auth.application.reauthenticate;

import com.flowops.auth.application.shared.ClientContext;

public record ReauthenticateCommand(String password, ClientContext clientContext) {}
