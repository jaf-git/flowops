package com.flowops.workspace.application.shared.exception;

public class InvitationNotYoursException extends InvitationRefusedException {
    public InvitationNotYoursException() {
        super("NOT_YOURS", "that invitation was created by somebody else");
    }
}
