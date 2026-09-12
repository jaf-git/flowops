package com.flowops.discovery.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.enums.NodeKind;
import com.flowops.discovery.domain.enums.OutputType;
import com.flowops.discovery.domain.enums.WorkNodeState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("DISCOVERY-VIEW-NODE-TRAIL-01")
class TheNodeRemembersEveryMoveItMadeTest {
    private static final UUID MARIA = UUID.randomUUID();
    private static final UUID ANDREI = UUID.randomUUID();
    private static final Instant NINE = Instant.parse("2026-08-31T09:00:00Z");

    private WorkNode marked(Direction direction) {
        return WorkNode.marked(
                WorkNodeId.of(UUID.randomUUID()),
                JobId.of(UUID.randomUUID()),
                "Andrei, poti sa scrii postarile pentru Aurora?",
                MARIA,
                null,
                NodeKind.WORK,
                direction,
                NINE);
    }

    private static List<WorkNodeState> statesOf(WorkNode node) {
        return node.pendingTransitions().stream().map(NodeStateTransition::to).toList();
    }

    @Test
    void aMarkedNodeOpensItsTrailFromNowhere() {
        WorkNode node = marked(Direction.REQUEST);

        assertThat(node.pendingTransitions()).hasSize(1);
        NodeStateTransition arrival = node.pendingTransitions().getFirst();
        assertThat(arrival.cameFrom()).isEmpty();
        assertThat(arrival.to()).isEqualTo(WorkNodeState.MARKED);
        assertThat(arrival.actor()).contains(MARIA);
    }

    @Test
    void aQuestionOpensItsTrailAtQuery() {
        assertThat(statesOf(marked(Direction.QUERY))).containsExactly(WorkNodeState.QUERY);
    }

    @Test
    void everyArrowOfAnOrdinaryLifeLeavesItsOwnRow() {
        WorkNode node = marked(Direction.REQUEST);

        node.requestedOf(ANDREI, null);
        node.started(NINE);
        node.blocked();
        node.resumed();
        node.completed(OutputType.TEXT);
        node.closed(NINE);

        assertThat(statesOf(node))
                .containsExactly(
                        WorkNodeState.MARKED,
                        WorkNodeState.ASSIGNED,
                        WorkNodeState.IN_PROGRESS,
                        WorkNodeState.BLOCKED,
                        WorkNodeState.IN_PROGRESS,
                        WorkNodeState.COMPLETED,
                        WorkNodeState.CLOSED);

        assertThat(node.state()).isEqualTo(WorkNodeState.CLOSED);
    }

    @Test
    void theSoloAndBounceArrowsAreRecordedToo() {
        WorkNode solo = marked(Direction.REQUEST);
        solo.keptForSelf();
        assertThat(statesOf(solo)).containsExactly(WorkNodeState.MARKED, WorkNodeState.SELF);

        WorkNode handed = marked(Direction.REQUEST);
        handed.requestedOf(ANDREI, null);
        handed.bounced();
        handed.reassignedTo(MARIA, null);
        assertThat(statesOf(handed))
                .containsExactly(
                        WorkNodeState.MARKED, WorkNodeState.ASSIGNED, WorkNodeState.BOUNCED, WorkNodeState.ASSIGNED);
    }

    @Test
    void theQueryBranchAndTheLoopAreRecordedWithTheirActorsWhereThereIsOne() {
        WorkNode question = marked(Direction.REQUEST);
        question.becameQuery();
        question.answered();

        assertThat(statesOf(question))
                .containsExactly(WorkNodeState.MARKED, WorkNodeState.QUERY, WorkNodeState.ANSWERED);

        assertThat(question.pendingTransitions().getLast().actor()).isEmpty();

        WorkNode revised = marked(Direction.REQUEST);
        revised.requestedOf(ANDREI, null);
        revised.started(NINE);
        revised.completed(OutputType.TEXT);
        revised.reopenedByLoop();

        assertThat(statesOf(revised)).endsWith(WorkNodeState.COMPLETED, WorkNodeState.IN_PROGRESS);
        assertThat(revised.pendingTransitions().getLast().actor()).isEmpty();
    }

    @Test
    void aDroppedNodeIsAttributedAndAnAbandonedOneIsNot() {
        WorkNode dropped = marked(Direction.REQUEST);
        dropped.requestedOf(ANDREI, null);
        dropped.lapsed(ANDREI);
        assertThat(dropped.pendingTransitions().getLast().actor()).contains(ANDREI);

        WorkNode abandoned = marked(Direction.REQUEST);
        abandoned.requestedOf(ANDREI, null);
        abandoned.lapsed();
        assertThat(abandoned.pendingTransitions().getLast().actor()).isEmpty();
    }

    @Test
    void aTransitionMachineFourOneRefusesLeavesNoRow() {
        WorkNode node = marked(Direction.REQUEST);

        assertThat(catchThrowableFrom(() -> node.started(NINE))).isNotNull();
        assertThat(statesOf(node)).containsExactly(WorkNodeState.MARKED);
    }

    @Test
    void noOutputYetIsAnAnswerRatherThanAMove() {
        WorkNode node = marked(Direction.REQUEST);
        node.requestedOf(ANDREI, null);
        node.started(NINE);

        node.completed(OutputType.NONE);

        assertThat(statesOf(node))
                .containsExactly(WorkNodeState.MARKED, WorkNodeState.ASSIGNED, WorkNodeState.IN_PROGRESS);
        assertThat(node.state()).isEqualTo(WorkNodeState.IN_PROGRESS);
    }

    @Test
    void aRehydratedNodeHasNothingToWriteDown() {
        WorkNode read = WorkNode.rehydrated(
                WorkNodeId.of(UUID.randomUUID()),
                JobId.of(UUID.randomUUID()),
                "already in the database",
                MARIA,
                null,
                NodeKind.WORK,
                NINE,
                WorkNodeState.CLOSED,
                Direction.REQUEST,
                null,
                null,
                ANDREI,
                null,
                OutputType.TEXT,
                NINE,
                NINE,
                null,
                null,
                null,
                null,
                "CONTENT",
                null,
                null,
                null,
                null);

        assertThat(read.pendingTransitions()).isEmpty();
    }

    @Test
    void whatHasBeenWrittenIsNotOfferedAgain() {
        WorkNode node = marked(Direction.REQUEST);
        node.requestedOf(ANDREI, null);
        assertThat(node.pendingTransitions()).hasSize(2);

        node.transitionsWritten();

        assertThat(node.pendingTransitions()).isEmpty();
        assertThat(node.state()).isEqualTo(WorkNodeState.ASSIGNED);
    }

    private static Throwable catchThrowableFrom(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException caught) {
            return caught;
        }
    }
}
