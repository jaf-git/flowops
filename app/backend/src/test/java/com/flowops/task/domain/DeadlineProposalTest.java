package com.flowops.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.task.domain.enums.ProposalDecision;
import com.flowops.task.domain.exception.DeadlineInThePastException;
import com.flowops.task.domain.exception.DeclineReasonRequiredException;
import com.flowops.task.domain.exception.NoOpenProposalException;
import com.flowops.task.domain.exception.ProposalIsStaleException;
import com.flowops.task.domain.exception.ProposalReasonRequiredException;
import com.flowops.task.domain.model.DeadlineProposal;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("TASK-PROPOSE-DEADLINE-01")
@Tag("TASK-DECIDE-DEADLINE-01")
class DeadlineProposalTest {
    private static final Instant NOW = Instant.parse("2026-08-11T14:00:00Z");
    private static final Instant NEXT_MONDAY = Instant.parse("2026-08-17T17:00:00Z");
    private static final TaskId TASK = TaskId.generate();
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());
    private static final PersonId IONUT = PersonId.of(UUID.randomUUID());

    @Test
    void aProposalIsMadeOpenWithTheDateAndTheReasonOnIt() {
        DeadlineProposal proposal =
                DeadlineProposal.proposed(TASK, NEXT_MONDAY, "The parts arrive Friday", ANDREI, NOW);

        assertThat(proposal.isOpen()).isTrue();
        assertThat(proposal.answer()).isEmpty();
        assertThat(proposal.proposedDeadline()).isEqualTo(NEXT_MONDAY);
        assertThat(proposal.reason()).isEqualTo("The parts arrive Friday");
        assertThat(proposal.proposer()).isEqualTo(ANDREI);
        assertThat(proposal.proposedAt()).isEqualTo(NOW);
    }

    @Test
    void aDateWithNoReasonIsRefused() {
        assertThatThrownBy(() -> DeadlineProposal.proposed(TASK, NEXT_MONDAY, "  ", ANDREI, NOW))
                .isInstanceOf(ProposalReasonRequiredException.class);
        assertThatThrownBy(() -> DeadlineProposal.proposed(TASK, NEXT_MONDAY, null, ANDREI, NOW))
                .isInstanceOf(ProposalReasonRequiredException.class);
    }

    @Test
    void aDateThatHasAlreadyPassedIsRefused() {
        assertThatThrownBy(() -> DeadlineProposal.proposed(
                        TASK, Instant.parse("2026-08-01T09:00:00Z"), "It should have been last week", ANDREI, NOW))
                .isInstanceOf(DeadlineInThePastException.class);
    }

    @Test
    void offeringToFinishSoonerIsAnOrdinaryProposal() {
        DeadlineProposal sooner = DeadlineProposal.proposed(
                TASK, Instant.parse("2026-08-12T09:00:00Z"), "The parts came early", ANDREI, NOW);

        assertThat(sooner.isOpen()).isTrue();
        assertThat(sooner.proposedDeadline()).isEqualTo(Instant.parse("2026-08-12T09:00:00Z"));
    }

    @Test
    void acceptingClosesItAndNamesWhoDecidedAndWhen() {
        DeadlineProposal answered = open().accepted(IONUT, NOW);

        assertThat(answered.isOpen()).isFalse();
        assertThat(answered.answer()).contains(ProposalDecision.ACCEPTED);
        assertThat(answered.decidedBy()).isEqualTo(IONUT);
        assertThat(answered.decidedAt()).isEqualTo(NOW);
        assertThat(answered.whyDeclined()).isEmpty();
    }

    @Test
    void decliningClosesItAndKeepsTheAnswerTheAssigneeIsOwed() {
        DeadlineProposal answered = open().declined(IONUT, "The client will not move the audit", NOW);

        assertThat(answered.isOpen()).isFalse();
        assertThat(answered.answer()).contains(ProposalDecision.DECLINED);
        assertThat(answered.whyDeclined()).contains("The client will not move the audit");
    }

    @Test
    void decliningInSilenceIsRefused() {
        assertThatThrownBy(() -> open().declined(IONUT, "   ", NOW)).isInstanceOf(DeclineReasonRequiredException.class);
        assertThatThrownBy(() -> open().declined(IONUT, null, NOW)).isInstanceOf(DeclineReasonRequiredException.class);
    }

    @Test
    void aDateThatExpiredWhileNobodyAnsweredCannotBeAccepted() {
        DeadlineProposal sat = DeadlineProposal.proposed(TASK, NEXT_MONDAY, "The parts arrive Friday", ANDREI, NOW);

        assertThatThrownBy(() -> sat.accepted(IONUT, Instant.parse("2026-08-18T09:00:00Z")))
                .isInstanceOf(ProposalIsStaleException.class);
    }

    @Test
    void aDateThatExpiredCanStillBeRefused() {
        DeadlineProposal sat = DeadlineProposal.proposed(TASK, NEXT_MONDAY, "The parts arrive Friday", ANDREI, NOW);

        DeadlineProposal answered =
                sat.declined(IONUT, "That has passed; propose another", Instant.parse("2026-08-18T09:00:00Z"));

        assertThat(answered.answer()).contains(ProposalDecision.DECLINED);
    }

    @Test
    void aProposalThatHasBeenAnsweredCannotBeAnsweredAgain() {
        DeadlineProposal accepted = open().accepted(IONUT, NOW);
        DeadlineProposal declined = open().declined(IONUT, "No", NOW);

        assertThatThrownBy(() -> accepted.accepted(IONUT, NOW)).isInstanceOf(NoOpenProposalException.class);
        assertThatThrownBy(() -> declined.declined(IONUT, "Still no", NOW)).isInstanceOf(NoOpenProposalException.class);
    }

    private static DeadlineProposal open() {
        return DeadlineProposal.proposed(TASK, NEXT_MONDAY, "The parts arrive Friday", ANDREI, NOW);
    }
}
