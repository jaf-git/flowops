package com.flowops.auth.domain.exception;

import com.flowops.auth.domain.enums.PasswordRule;
import java.util.List;

public class PasswordPolicyViolationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient List<PasswordRule> violations;

    public PasswordPolicyViolationException(List<PasswordRule> violations) {
        super("the password does not meet the policy");
        this.violations = List.copyOf(violations);
    }

    public List<PasswordRule> violations() {
        return violations;
    }
}
