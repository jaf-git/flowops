package com.flowops.workspace.application.shared.exception;

public class ReauthenticationRequiredException extends RuntimeException {
    public ReauthenticationRequiredException() {
        super("this action needs a current re-authentication");
    }
}
