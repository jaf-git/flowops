package com.flowops.workspace.application.shared.exception;

public class AlreadyMemberException extends InvitationRefusedException {
    public AlreadyMemberException(String message) {
        super("ALREADY_MEMBER", message);
    }
}
