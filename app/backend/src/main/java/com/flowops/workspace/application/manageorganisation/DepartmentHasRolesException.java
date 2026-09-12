package com.flowops.workspace.application.manageorganisation;

import java.util.UUID;

public class DepartmentHasRolesException extends RuntimeException {
    private final long roles;

    public DepartmentHasRolesException(UUID id, long roles) {
        super("department " + id + " still holds " + roles + " roles");
        this.roles = roles;
    }

    public long roles() {
        return roles;
    }
}
