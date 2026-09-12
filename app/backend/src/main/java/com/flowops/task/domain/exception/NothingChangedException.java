package com.flowops.task.domain.exception;

public class NothingChangedException extends RuntimeException {
    public NothingChangedException() {
        super("An edit that changes nothing");
    }
}
