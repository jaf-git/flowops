package com.flowops.workspace.application.shared.exception;

public class DeactivatedMemberException extends InvitationRefusedException {
    public DeactivatedMemberException(String message) {
        super("DEACTIVATED_MEMBER", message);
    }
}
