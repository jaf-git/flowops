package com.flowops.workspace.application.shared.exception;

public class ManagerNotEligibleForReportsException extends InvitationRefusedException {
    public ManagerNotEligibleForReportsException() {
        super("MANAGER_NOT_ELIGIBLE", "reports attach only to somebody holding the owner or manager role");
    }
}
