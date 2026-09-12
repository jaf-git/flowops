package com.flowops.process.domain.exception;

public class UnknownOwnerRoleException extends RuntimeException {
    public UnknownOwnerRoleException(String given) {
        super("a run is owned by OWNER, MANAGER or EMPLOYEE, not '" + given + "'");
    }
}
