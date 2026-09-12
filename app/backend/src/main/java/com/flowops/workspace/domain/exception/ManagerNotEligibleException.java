package com.flowops.workspace.domain.exception;

public class ManagerNotEligibleException extends RuntimeException {
    private final String code;

    public ManagerNotEligibleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
