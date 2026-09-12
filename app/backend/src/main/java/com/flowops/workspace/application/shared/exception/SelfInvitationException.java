package com.flowops.workspace.application.shared.exception;

public class SelfInvitationException extends InvitationRefusedException {
    public SelfInvitationException(String message) {
        super("SELF_INVITATION", message);
    }
}
