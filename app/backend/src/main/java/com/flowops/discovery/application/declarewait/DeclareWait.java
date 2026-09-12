package com.flowops.discovery.application.declarewait;

import com.flowops.discovery.application.shared.port.DiscoveryNoticePort;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.domain.enums.CloseKind;
import com.flowops.discovery.domain.enums.WaitKind;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.BracketInvariants;
import com.flowops.discovery.domain.model.WorkBracket;
import com.flowops.discovery.domain.model.WorkNodeWait;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeclareWait {
    private final WorkBracketPort brackets;
    private final DiscoveryNoticePort notices;
    private final Clock clock;

    public DeclareWait(WorkBracketPort brackets, DiscoveryNoticePort notices, Clock clock) {
        this.notices = notices;
        this.brackets = brackets;
        this.clock = clock;
    }

    @Transactional
    public WorkNodeWait declare(BracketId waiterId, WaitKind kind, BracketId on, String reason, Instant expectedBy) {
        Objects.requireNonNull(waiterId, "somebody is waiting");
        Objects.requireNonNull(kind, "R7.1 - a wait names what it is waiting on");

        WorkBracket waiter = mustFind(waiterId);
        WorkBracket target = on == null ? null : mustFind(on);

        Instant now = clock.instant();

        WorkNodeWait wait = WorkNodeWait.declared(brackets.nextWaitId(), waiter, kind, target, reason, expectedBy, now);

        brackets.save(wait);

        syncWaitingState(waiter, now);

        if (target != null
                && wait.satisfiedAt().isEmpty()
                && !target.closureRight().equals(waiter.closureRight())) {
            notices.somebodyIsWaitingOnYou(target.closureRight(), target.id());
        }

        return wait;
    }

    @Transactional
    public WorkNodeWait declareFor(
            UUID caller, BracketId waiterId, WaitKind kind, BracketId on, String reason, Instant expectedBy) {
        Objects.requireNonNull(waiterId, "somebody is waiting");

        WorkBracket blocked = mustFind(waiterId);
        if (!blocked.closureRight().equals(caller)) {
            throw new WaitIsNotYoursToDeclareException(waiterId.value());
        }

        return declare(waiterId, kind, on, reason, expectedBy);
    }

    @Transactional
    public void itArrived(WorkNodeWait wait) {
        if (wait.onBracketId().isPresent()) {
            throw new ItsTargetDecidesWhenItArrivedException(wait.id());
        }

        Instant now = clock.instant();
        wait.satisfiedBy(CloseKind.DONE, now);
        brackets.save(wait);

        brackets.find(wait.bracketId()).ifPresent(waiter -> syncWaitingState(waiter, now));
    }

    @Transactional
    public void itArrived(UUID waitId, UUID caller) {
        itArrived(mine(waitId, caller));
    }

    @Transactional
    public void withdraw(UUID waitId, UUID caller) {
        withdraw(mine(waitId, caller));
    }

    private WorkNodeWait mine(UUID waitId, UUID caller) {
        WorkNodeWait wait = brackets.findWait(waitId).orElseThrow(() -> new UnknownWaitException(waitId));

        if (wait.satisfiedAt().isPresent() || wait.cancelledAt().isPresent()) {
            throw new WaitAlreadyEndedException(waitId);
        }

        WorkBracket blocked = mustFind(wait.bracketId());
        if (!blocked.closureRight().equals(caller)) {
            throw new WaitIsNotYoursToEndException(waitId);
        }
        return wait;
    }

    @Transactional
    public void withdraw(WorkNodeWait wait) {
        Instant now = clock.instant();
        wait.withdrawn(now);
        brackets.save(wait);

        brackets.find(wait.bracketId()).ifPresent(waiter -> syncWaitingState(waiter, now));
    }

    private void syncWaitingState(WorkBracket waiter, Instant now) {
        int openWaits = brackets.openWaitsHeldBy(waiter.id()).size();

        waiter.waitingOnSomething(openWaits > 0);
        waiter.touched(now);
        brackets.save(waiter);

        BracketInvariants.assertAllOn(waiter, openWaits);
    }

    private WorkBracket mustFind(BracketId id) {
        return brackets.find(id).orElseThrow(() -> new IllegalArgumentException("no bracket " + id.value()));
    }
}
