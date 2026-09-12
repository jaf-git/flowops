package com.flowops.workspace.application.shared.exception;

public class DeclineWindowException extends InvitationRefusedException {
    public DeclineWindowException(String message) {
        super("DECLINE_WINDOW", message);
    }
}
