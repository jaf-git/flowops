package com.flowops.task.domain.exception;

public class ApprovalScoreOutOfRangeException extends RuntimeException {
    private final int offered;

    public ApprovalScoreOutOfRangeException(int offered) {
        super("a score is one to five; " + offered + " is not");
        this.offered = offered;
    }

    public int offered() {
        return offered;
    }
}
