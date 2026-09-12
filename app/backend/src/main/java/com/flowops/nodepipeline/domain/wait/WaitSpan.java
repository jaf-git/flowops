package com.flowops.nodepipeline.domain.wait;

import java.time.Instant;

public record WaitSpan(
        WaitKind kind,
        Instant openedAt,
        Instant satisfiedAt,
        Instant cancelledAt,
        BracketClose awaitedClose,
        Instant awaitedClosedAt) {
    public Instant endedBy(Instant stillWaitingAt) {
        if (cancelledAt != null) {
            return cancelledAt;
        }
        if (awaitedClose != null && !awaitedClose.completes()) {
            return stillWaitingAt;
        }
        if (satisfiedAt != null) {
            return satisfiedAt;
        }
        if (awaitedClose != null && awaitedClosedAt != null) {
            return awaitedClosedAt;
        }
        return stillWaitingAt;
    }
}
