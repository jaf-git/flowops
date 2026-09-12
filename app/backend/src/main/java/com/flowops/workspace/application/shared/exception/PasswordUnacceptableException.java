package com.flowops.workspace.application.shared.exception;

import java.util.List;

public class PasswordUnacceptableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient List<String> rules;

    public PasswordUnacceptableException(List<String> rules) {
        super("the password does not meet the policy");
        this.rules = List.copyOf(rules);
    }

    public List<String> rules() {
        return rules;
    }
}
