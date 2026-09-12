package com.flowops.workspace.domain.enums;

public enum InvitationState {
    AWAITING_APPROVAL,
    SENT,
    ACCEPTED,
    DECLINED,
    REVOKED,
    EXPIRED,
    REFUSED;

    public boolean isOpen() {
        return this == AWAITING_APPROVAL || this == SENT;
    }
}
