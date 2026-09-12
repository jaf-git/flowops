package com.flowops.workspace.application.shared.exception;

public class DuplicateInvitationException extends InvitationRefusedException {
    public DuplicateInvitationException(String message) {
        super("DUPLICATE_INVITATION", message);
    }
}
