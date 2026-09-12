package com.flowops.workspace.domain.exception;

import com.flowops.workspace.domain.model.MembershipId;
import java.util.List;

public class CycleWouldFormException extends RuntimeException {
    private final transient List<MembershipId> path;

    public CycleWouldFormException(List<MembershipId> path) {
        super("that move would make the reporting line loop back on itself");
        this.path = List.copyOf(path);
    }

    public List<MembershipId> path() {
        return path;
    }
}
