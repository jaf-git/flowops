package com.flowops.discovery.application.handover;

import com.flowops.discovery.application.closebracket.CloseBracket;
import com.flowops.discovery.application.closebracket.CloseOutcome;
import com.flowops.discovery.application.shared.port.PersonRolePort;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.domain.enums.NodeRole;
import com.flowops.discovery.domain.model.BracketAddress;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.BracketInvariants;
import com.flowops.discovery.domain.model.WorkBracket;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HandOverBracket {
    private final WorkBracketPort brackets;
    private final CloseBracket closing;
    private final PersonRolePort people;
    private final Clock clock;

    public HandOverBracket(WorkBracketPort brackets, CloseBracket closing, PersonRolePort people, Clock clock) {
        this.brackets = brackets;
        this.closing = closing;
        this.people = people;
        this.clock = clock;
    }

    @Transactional
    public Optional<Handover> handOver(
            BracketId id, UUID newPerformer, WorkNodeId successorStartNode, boolean causedByDeactivation) {
        Objects.requireNonNull(newPerformer, "R14.1 - a handover names who takes it on");

        WorkBracket predecessor =
                brackets.find(id).orElseThrow(() -> new IllegalArgumentException("no bracket " + id.value()));

        if (!people.isActiveMember(newPerformer)) {
            BracketInvariants.anOrphanHoldsNoBracket(successorStartNode, null, false);
            return Optional.empty();
        }

        BracketAddress successorAddress = predecessor.address().performedBy(newPerformer);

        WorkBracket successor = WorkBracket.continuing(
                brackets.nextBracketId(),
                predecessor,
                successorAddress,
                successorStartNode,
                newPerformer,
                causedByDeactivation,
                clock.instant());

        brackets.save(successor);

        boolean claimed = brackets.placeNode(
                successorStartNode,
                successor.id(),
                NodeRole.START,
                successorAddress,
                newPerformer,
                predecessor.openedByNode());

        if (!claimed) {
            brackets.appendStartNode(
                    successor, successorStartNode, predecessor.openedByNode(), newPerformer, clock.instant());
        }

        CloseOutcome outcome = closing.handedOver(predecessor.id(), successor);

        BracketInvariants.assertAllOn(
                successor, brackets.openWaitsHeldBy(successor.id()).size());

        return Optional.of(new Handover(predecessor.id(), successor, outcome));
    }

    @Transactional
    public void closureWalksUpTo(BracketId id, UUID manager) {
        Objects.requireNonNull(manager, "R5.1 - somebody must always hold the right to close this");

        WorkBracket bracket =
                brackets.find(id).orElseThrow(() -> new IllegalArgumentException("no bracket " + id.value()));

        bracket.closureTransfersTo(manager);
        brackets.save(bracket);

        BracketInvariants.everyOpenBracketIsClosable(bracket);
    }

    public record Handover(BracketId closed, WorkBracket successor, CloseOutcome outcome) {}
}
