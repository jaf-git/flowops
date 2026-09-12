package com.flowops.workspace.application.manageorganisation;

public class FunctionalRoleNameTakenException extends RuntimeException {
    public FunctionalRoleNameTakenException(String name) {
        super("a role already goes by " + name);
    }
}
