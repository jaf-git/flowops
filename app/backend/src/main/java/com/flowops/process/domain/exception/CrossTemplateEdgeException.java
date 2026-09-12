package com.flowops.process.domain.exception;

public class CrossTemplateEdgeException extends RuntimeException {
    public CrossTemplateEdgeException() {
        super("a dependency joins two steps of the same process");
    }
}
