package com.flowops.workspace.domain.exception;

public class AtRiskWindowInvalidException extends RuntimeException {
    public AtRiskWindowInvalidException() {
        super("an at-risk window is a positive number of hours");
    }
}
