package com.flowops.auth.domain.model;

import com.flowops.auth.domain.enums.LandingTarget;
import java.util.Locale;
import java.util.Objects;

public record RoleName(String value) {
    public static final RoleName OWNER = new RoleName("OWNER");

    public static final RoleName MANAGER = new RoleName("MANAGER");
    public static final RoleName EMPLOYEE = new RoleName("EMPLOYEE");

    public RoleName {
        Objects.requireNonNull(value, "a role is required");
        value = value.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("a role name cannot be blank");
        }
    }

    public LandingTarget landingTarget() {
        if (equals(OWNER) || equals(MANAGER)) {
            return LandingTarget.TRIAGE;
        }
        return LandingTarget.MY_WORK;
    }

    public boolean ownsWorkspaceSetup() {
        return equals(OWNER);
    }
}
