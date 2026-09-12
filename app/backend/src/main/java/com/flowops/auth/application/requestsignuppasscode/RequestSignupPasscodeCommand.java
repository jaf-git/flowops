package com.flowops.auth.application.requestsignuppasscode;

import com.flowops.auth.application.shared.ClientContext;

public record RequestSignupPasscodeCommand(String email, ClientContext clientContext) {}
