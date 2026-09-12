package com.flowops.workspace.domain.exception;

public class UnknownTimezoneException extends RuntimeException {
    private final String offered;

    public UnknownTimezoneException(String offered) {
        super("not a known timezone");
        this.offered = offered;
    }

    public String offered() {
        return offered;
    }
}
