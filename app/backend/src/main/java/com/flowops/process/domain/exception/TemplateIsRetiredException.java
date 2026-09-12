package com.flowops.process.domain.exception;

public class TemplateIsRetiredException extends RuntimeException {
    public TemplateIsRetiredException() {
        super("this template has been retired");
    }
}
