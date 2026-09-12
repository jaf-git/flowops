package com.flowops.workspace.application.shared.exception;

public class RoleCeilingException extends InvitationRefusedException {
    public RoleCeilingException(String message) {
        super("ROLE_CEILING", message);
    }
}
