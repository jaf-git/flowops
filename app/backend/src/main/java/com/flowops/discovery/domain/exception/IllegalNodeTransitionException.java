package com.flowops.discovery.domain.exception;

import com.flowops.discovery.domain.enums.WorkNodeState;

public class IllegalNodeTransitionException extends RuntimeException {
    private final transient WorkNodeState from;
    private final transient WorkNodeState to;

    public IllegalNodeTransitionException(WorkNodeState from, WorkNodeState to) {
        super("a unit of work cannot go from " + from + " to " + to + "; machine 17.1 draws no such transition");
        this.from = from;
        this.to = to;
    }

    public WorkNodeState from() {
        return from;
    }

    public WorkNodeState to() {
        return to;
    }
}
