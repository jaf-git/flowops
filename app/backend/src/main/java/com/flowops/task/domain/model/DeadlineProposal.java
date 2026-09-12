package com.flowops.task.domain.model;

import com.flowops.task.domain.enums.ProposalDecision;
import com.flowops.task.domain.exception.DeadlineInThePastException;
import com.flowops.task.domain.exception.DeclineReasonRequiredException;
import com.flowops.task.domain.exception.NoOpenProposalException;
import com.flowops.task.domain.exception.ProposalIsStaleException;
import com.flowops.task.domain.exception.ProposalReasonRequiredException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record DeadlineProposal(
        DeadlineProposalId id,
        TaskId task,
        Instant proposedDeadline,
        String reason,
        PersonId proposer,
        Instant proposedAt,
        ProposalDecision decision,
        String decisionReason,
        PersonId decidedBy,
        Instant decidedAt) {
    public static DeadlineProposal proposed(
            TaskId task, Instant proposedDeadline, String reason, PersonId proposer, Instant now) {
        String written = reason == null ? "" : reason.trim();
        if (written.isEmpty()) {
            throw new ProposalReasonRequiredException();
        }
        Objects.requireNonNull(proposedDeadline, "a proposal is a date");
        if (!proposedDeadline.isAfter(now)) {
            throw new DeadlineInThePastException();
        }

        return new DeadlineProposal(
                DeadlineProposalId.generate(), task, proposedDeadline, written, proposer, now, null, null, null, null);
    }

    public static DeadlineProposal rebuild(
            DeadlineProposalId id,
            TaskId task,
            Instant proposedDeadline,
            String reason,
            PersonId proposer,
            Instant proposedAt,
            ProposalDecision decision,
            String decisionReason,
            PersonId decidedBy,
            Instant decidedAt) {
        return new DeadlineProposal(
                id,
                task,
                proposedDeadline,
                reason,
                proposer,
                proposedAt,
                decision,
                decisionReason,
                decidedBy,
                decidedAt);
    }

    public DeadlineProposal accepted(PersonId decider, Instant at) {
        refuseASecondAnswer();

        if (!proposedDeadline.isAfter(at)) {
            throw new ProposalIsStaleException();
        }
        return new DeadlineProposal(
                id, task, proposedDeadline, reason, proposer, proposedAt, ProposalDecision.ACCEPTED, null, decider, at);
    }

    public DeadlineProposal declined(PersonId decider, String reason, Instant at) {
        refuseASecondAnswer();
        String written = reason == null ? "" : reason.trim();
        if (written.isEmpty()) {
            throw new DeclineReasonRequiredException();
        }

        return new DeadlineProposal(
                id,
                task,
                proposedDeadline,
                this.reason,
                proposer,
                proposedAt,
                ProposalDecision.DECLINED,
                written,
                decider,
                at);
    }

    private void refuseASecondAnswer() {
        if (!isOpen()) {
            throw new NoOpenProposalException();
        }
    }

    public boolean isOpen() {
        return decision == null;
    }

    public Optional<ProposalDecision> answer() {
        return Optional.ofNullable(decision);
    }

    public Optional<String> whyDeclined() {
        return Optional.ofNullable(decisionReason);
    }
}
