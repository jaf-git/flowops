package com.flowops.workspace.domain.exception;

public class EscalationIntervalsUnorderedException extends RuntimeException {
    private final int earlier;
    private final int later;

    public EscalationIntervalsUnorderedException(int earlier, int later) {
        super("escalation intervals ascend; positions " + earlier + " and " + later + " do not");
        this.earlier = earlier;
        this.later = later;
    }

    public int earlier() {
        return earlier;
    }

    public int later() {
        return later;
    }
}
