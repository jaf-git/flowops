package com.flowops.workspace.domain.exception;

public class AnalysisThresholdInvalidException extends RuntimeException {
    private final String field;

    public AnalysisThresholdInvalidException(String field) {
        super(field + " is outside the range in which it means anything");
        this.field = field;
    }

    public String field() {
        return field;
    }
}
