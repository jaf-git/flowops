package com.flowops.workspace.application.shared.exception;

public class InvitationNotFoundException extends InvitationRefusedException {
    public InvitationNotFoundException() {
        super("INVITATION_NOT_FOUND", "that invitation does not exist");
    }
}
