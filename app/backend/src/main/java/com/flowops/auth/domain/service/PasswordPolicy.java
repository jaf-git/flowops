package com.flowops.auth.domain.service;

import com.flowops.auth.domain.enums.PasswordRule;
import com.flowops.auth.domain.model.EmailAddress;
import java.util.ArrayList;
import java.util.List;

public final class PasswordPolicy {
    public static final int MINIMUM_LENGTH = 12;
    public static final int MAXIMUM_LENGTH = 128;

    public List<PasswordRule> violationsFor(String password, EmailAddress email) {
        List<PasswordRule> violations = new ArrayList<>();
        if (password == null || password.length() < MINIMUM_LENGTH) {
            violations.add(PasswordRule.MINIMUM_LENGTH);
            return List.copyOf(violations);
        }
        if (password.length() > MAXIMUM_LENGTH) {
            violations.add(PasswordRule.MAXIMUM_LENGTH);
        }
        if (password.equalsIgnoreCase(email.localPart())) {
            violations.add(PasswordRule.NOT_EMAIL_LOCAL_PART);
        }
        return List.copyOf(violations);
    }

    public List<PasswordRule> violationsForChange(String password, EmailAddress email, boolean matchesCurrent) {
        List<PasswordRule> violations = new ArrayList<>(violationsFor(password, email));
        if (matchesCurrent) {
            violations.add(PasswordRule.NOT_CURRENT_PASSWORD);
        }
        return List.copyOf(violations);
    }
}
