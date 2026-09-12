package com.flowops.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.task.domain.model.Approval;
import com.flowops.task.domain.model.ApprovalScore;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("TASK-APPROVE-01")
class ApprovalTest {
    private static final Instant NOW = Instant.parse("2026-08-11T14:00:00Z");
    private static final PersonId IONUT = PersonId.of(UUID.randomUUID());

    @Test
    void anApprovalCarriesTheScoreTheReviewerAndWhenItWasDecided() {
        TaskId task = TaskId.generate();

        Approval approval = Approval.of(task, ApprovalScore.of(4), "Clear and on time.", IONUT, NOW);

        assertThat(approval.task()).isEqualTo(task);
        assertThat(approval.score().value()).isEqualTo(4);
        assertThat(approval.reviewer()).isEqualTo(IONUT);
        assertThat(approval.decidedAt()).isEqualTo(NOW);
        assertThat(approval.writtenComment()).contains("Clear and on time.");
    }

    @Test
    void aCommentIsTrimmedBeforeItIsStored() {
        Approval approval =
                Approval.of(TaskId.generate(), ApprovalScore.of(3), "  Needed a second pass.  ", IONUT, NOW);

        assertThat(approval.comment()).isEqualTo("Needed a second pass.");
    }

    @Test
    void aBlankCommentIsStoredAsNoCommentAtAll() {
        Approval approval = Approval.of(TaskId.generate(), ApprovalScore.of(5), "   ", IONUT, NOW);

        assertThat(approval.comment()).isNull();
        assertThat(approval.writtenComment()).isEmpty();
    }

    @Test
    void anApprovalWithNoCommentIsPerfectlyOrdinary() {
        assertThat(Approval.of(TaskId.generate(), ApprovalScore.of(5), null, IONUT, NOW)
                        .writtenComment())
                .isEmpty();
    }
}
