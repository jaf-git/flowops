package com.flowops.workspace.domain.exception;

public class NoWorkingDaysException extends RuntimeException {
    public NoWorkingDaysException() {
        super("a working week has at least one working day");
    }
}
