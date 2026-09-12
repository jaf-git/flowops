package com.flowops.task.domain.exception;

public class BlockReasonRequiredException extends RuntimeException {
    public BlockReasonRequiredException() {
        super("a block says why, or it is indistinguishable from stopping work");
    }
}
