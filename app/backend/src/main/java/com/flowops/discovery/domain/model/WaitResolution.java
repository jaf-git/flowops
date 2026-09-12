package com.flowops.discovery.domain.model;

import com.flowops.discovery.domain.enums.CloseKind;
import java.util.Objects;

public enum WaitResolution {
    SATISFIED,

    DIED,

    RE_TARGETS;

    public static WaitResolution of(CloseKind closeKind) {
        Objects.requireNonNull(closeKind, "a bracket that ended, ended some way");

        return switch (closeKind) {
            case DELIVERED, DONE -> SATISFIED;
            case HANDED_OVER, MERGED -> RE_TARGETS;
            case DROPPED, LAPSED, PARENT_CLOSED, OVERRIDE, CADENCE_CLOSED, JOB_END -> DIED;
        };
    }
}
