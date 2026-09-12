package com.flowops.task.domain.exception;

public class NoOpenProposalException extends RuntimeException {
    public NoOpenProposalException() {
        super("A decision about nothing");
    }
}
