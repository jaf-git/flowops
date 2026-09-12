package com.flowops.discovery.domain.model;

import com.flowops.discovery.domain.enums.LoopExit;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class Loop {
    private final UUID id;
    private final JobId jobId;
    private final Set<WorkNodeId> memberNodeIds;

    private int cycleCount;
    private LoopExit exit;
    private long totalWorkMs;
    private long totalExternalWaitMs;

    private Loop(
            UUID id,
            JobId jobId,
            Collection<WorkNodeId> memberNodeIds,
            int cycleCount,
            LoopExit exit,
            long totalWorkMs,
            long totalExternalWaitMs) {
        this.id = Objects.requireNonNull(id, "a loop needs an identity");
        this.jobId = Objects.requireNonNull(jobId, "a loop lives inside one engagement — invariant I2");
        this.memberNodeIds = new LinkedHashSet<>(Objects.requireNonNull(memberNodeIds));
        if (this.memberNodeIds.size() < 2) {
            throw new IllegalArgumentException("a loop is a cycle between at least two units of work");
        }
        this.cycleCount = cycleCount;
        this.exit = exit;
        this.totalWorkMs = totalWorkMs;
        this.totalExternalWaitMs = totalExternalWaitMs;
    }

    public static Loop suspected(UUID id, JobId jobId, List<WorkNodeId> members) {
        return new Loop(id, jobId, members, 2, null, 0L, 0L);
    }

    public static Loop rehydrated(
            UUID id,
            JobId jobId,
            List<WorkNodeId> memberNodeIds,
            int cycleCount,
            LoopExit exit,
            long totalWorkMs,
            long totalExternalWaitMs) {
        return new Loop(id, jobId, memberNodeIds, cycleCount, exit, totalWorkMs, totalExternalWaitMs);
    }

    public void cycled() {
        refuseIfClosed();
        this.cycleCount++;
    }

    public void accumulated(Duration work, Duration externalWait) {
        refuseIfClosed();
        this.totalWorkMs += Objects.requireNonNull(work).toMillis();
        this.totalExternalWaitMs += Objects.requireNonNull(externalWait).toMillis();
    }

    public void closedWith(LoopExit howItEnded) {
        refuseIfClosed();
        this.exit = Objects.requireNonNull(
                howItEnded, "a loop cannot close without an exit answer; approved and cancelled are not averageable");
    }

    public boolean isConfirmed(int cyclesToConfirm) {
        return cycleCount >= cyclesToConfirm;
    }

    public boolean isClosed() {
        return exit != null;
    }

    private void refuseIfClosed() {
        if (isClosed()) {
            throw new IllegalStateException(
                    "loop " + id + " ended in " + exit + "; a cycle that starts again is a new loop, not this one");
        }
    }

    public UUID id() {
        return id;
    }

    public JobId jobId() {
        return jobId;
    }

    public Set<WorkNodeId> memberNodeIds() {
        return Collections.unmodifiableSet(memberNodeIds);
    }

    public int cycleCount() {
        return cycleCount;
    }

    public Optional<LoopExit> exit() {
        return Optional.ofNullable(exit);
    }

    public long totalWorkMs() {
        return totalWorkMs;
    }

    public long totalExternalWaitMs() {
        return totalExternalWaitMs;
    }
}
