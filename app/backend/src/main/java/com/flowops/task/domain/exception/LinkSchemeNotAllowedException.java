package com.flowops.task.domain.exception;

public class LinkSchemeNotAllowedException extends RuntimeException {
    public LinkSchemeNotAllowedException(String attempted) {
        super("only http and https links are stored; refused: " + attempted);
    }
}
