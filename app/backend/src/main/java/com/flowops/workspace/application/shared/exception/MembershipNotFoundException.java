package com.flowops.workspace.application.shared.exception;

public class MembershipNotFoundException extends RuntimeException {
    public MembershipNotFoundException() {
        super("no such person in this workspace");
    }
}
