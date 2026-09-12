package com.flowops.task.domain.exception;

public class LinkUrlRequiredException extends RuntimeException {
    public LinkUrlRequiredException() {
        super("a link needs an address");
    }
}
