package com.flowops.discovery.domain.enums;

public enum WaitingOn {
    CLIENT,

    COLLEAGUE,

    SUPPLIER,

    APPROVAL;

    public PhaseKind phaseKind() {
        return switch (this) {
            case CLIENT, SUPPLIER -> PhaseKind.EXTERNAL_WAIT;
            case COLLEAGUE, APPROVAL -> PhaseKind.INTERNAL_WAIT;
        };
    }
}
