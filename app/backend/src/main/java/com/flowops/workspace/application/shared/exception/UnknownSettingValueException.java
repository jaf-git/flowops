package com.flowops.workspace.application.shared.exception;

public class UnknownSettingValueException extends RuntimeException {
    private final String field;

    public UnknownSettingValueException(String field) {
        super("the value given for " + field + " is not one this field can hold");
        this.field = field;
    }

    public String field() {
        return field;
    }
}
