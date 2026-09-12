package com.flowops.workspace.domain.exception;

public class OwnerHasNoManagerException extends RuntimeException {
    public OwnerHasNoManagerException() {
        super("the owner is the root of the reporting line and has no manager");
    }
}
