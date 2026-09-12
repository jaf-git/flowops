package com.flowops.task.domain.exception;

public class ApprovalScoreRequiredException extends RuntimeException {
    public ApprovalScoreRequiredException() {
        super("approving is saying how the work was, not only that it was accepted");
    }
}
