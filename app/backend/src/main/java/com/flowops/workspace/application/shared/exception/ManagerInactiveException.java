package com.flowops.workspace.application.shared.exception;

public class ManagerInactiveException extends InvitationRefusedException {
    public ManagerInactiveException(String message) {
        super("MANAGER_INACTIVE", message);
    }
}
