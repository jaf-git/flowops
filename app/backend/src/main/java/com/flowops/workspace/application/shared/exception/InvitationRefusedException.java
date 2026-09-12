package com.flowops.workspace.application.shared.exception;

public abstract class InvitationRefusedException extends RuntimeException {
    private final String code;

    protected InvitationRefusedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
