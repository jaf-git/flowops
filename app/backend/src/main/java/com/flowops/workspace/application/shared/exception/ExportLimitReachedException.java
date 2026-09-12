package com.flowops.workspace.application.shared.exception;

public class ExportLimitReachedException extends RuntimeException {
    public ExportLimitReachedException() {
        super("too many exports have been produced for this person recently");
    }
}
