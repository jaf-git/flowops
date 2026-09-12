package com.flowops.process.application.shared.exception;

public class InstanceNotFoundException extends RuntimeException {
    public InstanceNotFoundException() {
        super("there is no such process run");
    }
}
