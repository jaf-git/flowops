package com.flowops.notification.application.shared.exception;

public class NotADisableableGroupException extends RuntimeException {
    private final String group;

    public NotADisableableGroupException(String group) {
        super("%s is not a group anybody can switch off".formatted(group));
        this.group = group;
    }

    public String group() {
        return group;
    }
}
