package com.flowops.workspace.application.shared.exception;

public class InvitationNotUsableException extends RuntimeException {
    public InvitationNotUsableException() {
        super("that invitation link cannot be used; ask whoever invited you for a new one");
    }
}
