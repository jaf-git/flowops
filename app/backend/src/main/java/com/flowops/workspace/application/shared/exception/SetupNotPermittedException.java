package com.flowops.workspace.application.shared.exception;

public class SetupNotPermittedException extends RuntimeException {
    public SetupNotPermittedException() {
        super("workspace setup is not this caller's to perform");
    }
}
