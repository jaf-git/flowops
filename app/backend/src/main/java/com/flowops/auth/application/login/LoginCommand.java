package com.flowops.auth.application.login;

import com.flowops.auth.application.shared.ClientContext;

public record LoginCommand(String email, String password, ClientContext clientContext) {}
