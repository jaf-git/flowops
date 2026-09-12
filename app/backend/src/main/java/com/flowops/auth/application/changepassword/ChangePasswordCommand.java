package com.flowops.auth.application.changepassword;

import com.flowops.auth.application.shared.ClientContext;

public record ChangePasswordCommand(String newPassword, ClientContext clientContext) {}
