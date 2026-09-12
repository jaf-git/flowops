package com.flowops.auth.application.createinvitedaccount;

public class InvitedAddressTakenException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvitedAddressTakenException() {
        super("an account already exists for that address");
    }
}
