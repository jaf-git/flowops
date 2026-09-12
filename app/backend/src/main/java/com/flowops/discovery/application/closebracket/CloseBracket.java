package com.flowops.discovery.application.closebracket;

import com.flowops.discovery.application.shared.port.DiscoveryNoticePort;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.CloseKind;
import com.flowops.discovery.domain.enums.OutputKind;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.BracketInvariants;
import com.flowops.discovery.domain.model.WaitResolution;
import com.flowops.discovery.domain.model.WorkBracket;
import com.flowops.discovery.domain.model.WorkNodeId;
import com.flowops.discovery.domain.model.WorkNodeWait;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CloseBracket {
    private final WorkBracketPort brackets;
    private final WorkGraphPort jobs;
    private final DiscoveryNoticePort notices;
    private final Clock clock;

    public CloseBracket(WorkBracketPort brackets, WorkGraphPort jobs, DiscoveryNoticePort notices, Clock clock) {
        this.brackets = brackets;
        this.jobs = jobs;
        this.notices = notices;
        this.clock = clock;
    }

    @Transactional
    public CloseOutcome delivered(BracketId id, OutputKind kind, String value, UUID by) {
        WorkBracket bracket = mustFind(id);
        refuseIfNotEntitled(bracket, by);

        Instant now = clock.instant();
        bracket.delivered(endTheChain(bracket, by, now), kind, value, now);

        return finish(bracket, now, null, by);
    }

    @Transactional
    public CloseOutcome done(BracketId id, UUID by) {
        WorkBracket bracket = mustFind(id);
        refuseIfNotEntitled(bracket, by);

        Instant now = clock.instant();
        bracket.done(endTheChain(bracket, by, now), now);

        return finish(bracket, now, null, by);
    }

    @Transactional
    public CloseOutcome dropped(BracketId id, String reason, UUID by) {
        WorkBracket bracket = mustFind(id);
        refuseIfNotEntitled(bracket, by);

        Instant now = clock.instant();
        bracket.dropped(endTheChain(bracket, by, now), reason, now);

        return finish(bracket, now, null, by);
    }

    private WorkNodeId endTheChain(WorkBracket bracket, UUID by, Instant now) {
        return brackets.appendEndNode(bracket, by, now);
    }

    @Transactional
    public CloseOutcome selfClosed(BracketId id, OutputKind kind, String value, UUID by) {
        return kind == null ? done(id, by) : delivered(id, kind, value, by);
    }

    @Transactional
    public CloseOutcome forceClosed(BracketId id) {
        WorkBracket bracket = mustFind(id);

        Instant now = clock.instant();
        bracket.forceClosed(now);

        return finish(bracket, now, null, null);
    }

    @Transactional
    public CloseOutcome handedOver(BracketId id, WorkBracket successor) {
        Objects.requireNonNull(successor, "R14.1 - a handover names who took it on");

        WorkBracket bracket = mustFind(id);
        Instant now = clock.instant();
        bracket.handedOver(now);

        return finish(bracket, now, successor, null);
    }

    @Transactional
    public CloseOutcome merged(BracketId id, WorkBracket survivor) {
        Objects.requireNonNull(survivor, "R2.1 - a merge names the bracket that carries the work on");

        WorkBracket folded = mustFind(id);
        Instant now = clock.instant();
        folded.merged(now);

        return finish(folded, now, survivor, null);
    }

    @Transactional
    public CloseOutcome terminalisedByJobEnding(BracketId id) {
        WorkBracket bracket = mustFind(id);

        Instant now = clock.instant();
        bracket.closedByParent(now);

        return finish(bracket, now, null, null, NoticePolicy.THE_WHOLE_JOB_IS_ENDING);
    }

    @Transactional
    public CloseOutcome closedByCadence(BracketId id) {
        WorkBracket bracket = mustFind(id);

        Instant now = clock.instant();
        bracket.closedByCadence(now);

        return finish(bracket, now, null, null);
    }

    @Transactional
    public CloseOutcome lapsed(BracketId id) {
        WorkBracket bracket = mustFind(id);

        Instant now = clock.instant();
        bracket.lapsed(now);

        return finish(bracket, now, null, null);
    }

    private enum NoticePolicy {
        TELL_THE_WAITERS,

        THE_WHOLE_JOB_IS_ENDING
    }

    private CloseOutcome finish(WorkBracket bracket, Instant now, WorkBracket successor, UUID closedBy) {
        return finish(bracket, now, successor, closedBy, NoticePolicy.TELL_THE_WAITERS);
    }

    private CloseOutcome finish(
            WorkBracket bracket, Instant now, WorkBracket successor, UUID closedBy, NoticePolicy policy) {
        brackets.save(bracket);

        CloseKind kind = bracket.closeKind().orElseThrow();

        List<WorkNodeWait> released = new ArrayList<>();
        List<WorkNodeWait> bereaved = new ArrayList<>();
        List<WorkNodeWait> reTargeted = new ArrayList<>();

        releaseWaitsOn(bracket, kind, now, successor, closedBy, policy, released, bereaved, reTargeted);

        List<UUID> noticesToCancel = new ArrayList<>();
        bereaved.forEach(w -> noticesToCancel.add(w.id()));
        reTargeted.forEach(w -> noticesToCancel.add(w.id()));

        BracketInvariants.everyOpenBracketIsClosable(bracket);

        if (kind == CloseKind.DELIVERED) {
            jobs.findJob(bracket.jobId())
                    .ifPresent(job -> brackets.publishArtifact(
                            bracket, job.counterpartyId().orElse(null)));
        }

        if (kind.leavesAHole()) {
            jobs.findJob(bracket.jobId()).ifPresent(job -> {
                job.holdsAHole();
                jobs.save(job);
            });
        }

        return new CloseOutcome(
                bracket.id(), bracket.jobId(), released, bereaved, reTargeted, List.of(), noticesToCancel);
    }

    private void releaseWaitsOn(
            WorkBracket closed,
            CloseKind kind,
            Instant now,
            WorkBracket successor,
            UUID closedBy,
            NoticePolicy policy,
            List<WorkNodeWait> released,
            List<WorkNodeWait> bereaved,
            List<WorkNodeWait> reTargeted) {
        WaitResolution resolution = WaitResolution.of(kind);

        for (WorkNodeWait wait : brackets.openWaitsOn(closed.id())) {
            switch (resolution) {
                case SATISFIED -> {
                    BracketInvariants.aWaitIsSatisfiedOnlyByACompletion(kind, true);
                    wait.satisfiedBy(kind, now);
                    brackets.save(wait);
                    released.add(wait);
                    unblockIfNothingElseHoldsIt(wait, now)
                            .filter(waiter -> policy == NoticePolicy.TELL_THE_WAITERS)
                            .ifPresent(waiter -> tellThemItArrived(waiter, closedBy));
                }
                case DIED -> {
                    wait.cancelledBecauseItDied(now);
                    brackets.save(wait);
                    bereaved.add(wait);
                    unblockIfNothingElseHoldsIt(wait, now)
                            .filter(waiter -> policy == NoticePolicy.TELL_THE_WAITERS)
                            .ifPresent(this::tellThemItDied);
                }
                case RE_TARGETS -> {
                    WorkNodeWait moved = wait.reTargetedTo(brackets.nextWaitId(), successor);
                    wait.withdrawn(now);
                    brackets.save(wait);
                    brackets.save(moved);
                    reTargeted.add(moved);
                }

                default -> throw new IllegalStateException("no handling for wait resolution " + resolution
                        + " on bracket " + closed.id().value());
            }
        }
    }

    private Optional<WorkBracket> unblockIfNothingElseHoldsIt(WorkNodeWait cleared, Instant now) {
        Optional<WorkBracket> found = brackets.find(cleared.bracketId());
        if (found.isEmpty() || found.get().state().isTerminal()) {
            return Optional.empty();
        }

        WorkBracket waiter = found.get();
        int stillOpen = brackets.openWaitsHeldBy(waiter.id()).size();
        waiter.waitingOnSomething(stillOpen > 0);
        waiter.touched(now);
        brackets.save(waiter);

        BracketInvariants.waitingStateMatchesTheWaits(waiter, stillOpen);
        return Optional.of(waiter);
    }

    private void tellThemItArrived(WorkBracket waiter, UUID closedBy) {
        if (Objects.equals(waiter.closureRight(), closedBy)) {
            return;
        }
        BracketInvariants.noNoticeAboutAClosedBracket(waiter);
        notices.awaitedWorkArrived(waiter.closureRight(), waiter.id());
    }

    private void tellThemItDied(WorkBracket waiter) {
        BracketInvariants.noNoticeAboutAClosedBracket(waiter);
        notices.awaitedWorkDied(waiter.closureRight(), waiter.id());
    }

    private void refuseIfNotEntitled(WorkBracket bracket, UUID person) {
        if (!bracket.mayBeClosedBy(person)) {
            throw new IllegalStateException("R5 - " + person + " does not hold the closure right on bracket "
                    + bracket.id().value() + "; it sits with " + bracket.closureRight()
                    + ", and the job owner may always force-close");
        }
    }

    private WorkBracket mustFind(BracketId id) {
        return brackets.find(id).orElseThrow(() -> new IllegalArgumentException("no bracket " + id.value()));
    }
}
