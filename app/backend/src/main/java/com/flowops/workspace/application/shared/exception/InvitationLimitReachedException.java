package com.flowops.workspace.application.shared.exception;

public class InvitationLimitReachedException extends InvitationRefusedException {
    public InvitationLimitReachedException(String message) {
        super("RATE_LIMIT", message);
    }
}
