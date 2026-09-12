package com.flowops.auth.application.createinvitedaccount;

public class InvitedNameRequiredException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvitedNameRequiredException() {
        super("the name colleagues will see is required");
    }
}
