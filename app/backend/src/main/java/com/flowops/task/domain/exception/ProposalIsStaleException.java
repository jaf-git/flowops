package com.flowops.task.domain.exception;

public class ProposalIsStaleException extends RuntimeException {
    public ProposalIsStaleException() {
        super("A proposed date that passed while nobody answered");
    }
}
