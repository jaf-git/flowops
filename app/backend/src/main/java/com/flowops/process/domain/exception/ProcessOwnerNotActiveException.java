package com.flowops.process.domain.exception;

public class ProcessOwnerNotActiveException extends RuntimeException {
    public ProcessOwnerNotActiveException() {
        super("a process needs somebody who can steer it");
    }
}
