package com.flowops.auth.application.createinvitedaccount;

import java.util.List;

public class InvitedPasswordRefusedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient List<String> rules;

    public InvitedPasswordRefusedException(List<String> rules) {
        super("the password does not meet the policy");
        this.rules = List.copyOf(rules);
    }

    public List<String> rules() {
        return rules;
    }
}
