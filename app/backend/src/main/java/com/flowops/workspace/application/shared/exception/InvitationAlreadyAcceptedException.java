package com.flowops.workspace.application.shared.exception;

public class InvitationAlreadyAcceptedException extends InvitationRefusedException {
    private final String memberName;

    public InvitationAlreadyAcceptedException(String memberName) {
        super("ALREADY_ACCEPTED", "that invitation has been accepted and the person is now a member");
        this.memberName = memberName;
    }

    public String memberName() {
        return memberName;
    }
}
