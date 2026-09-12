package com.flowops.task.domain.exception;

public class ProposalAlreadyOpenException extends RuntimeException {
    private final java.time.Instant openProposalDeadline;

    public ProposalAlreadyOpenException(java.time.Instant openProposalDeadline) {
        super("a proposal is already open on this task");
        this.openProposalDeadline = openProposalDeadline;
    }

    public java.time.Instant openProposalDeadline() {
        return openProposalDeadline;
    }
}
