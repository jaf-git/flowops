package com.flowops.workspace.application.shared.exception;

public class MemberNameRequiredException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MemberNameRequiredException() {
        super("the name colleagues will see is required");
    }
}
