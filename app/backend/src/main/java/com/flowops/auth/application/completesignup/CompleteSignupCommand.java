package com.flowops.auth.application.completesignup;

import com.flowops.auth.application.shared.ClientContext;

public record CompleteSignupCommand(String email, String passcode, String password, ClientContext clientContext) {}
