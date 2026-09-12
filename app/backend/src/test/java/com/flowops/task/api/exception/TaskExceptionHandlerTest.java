package com.flowops.task.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.shared.web.ErrorResponse;
import com.flowops.task.domain.exception.ApprovalScoreOutOfRangeException;
import com.flowops.task.domain.exception.ApprovalScoreRequiredException;
import com.flowops.task.domain.exception.NothingChangedException;
import com.flowops.task.domain.exception.ProposalIsStaleException;
import com.flowops.task.domain.exception.ReworkReasonRequiredException;
import com.flowops.task.domain.exception.TaskIsClosedException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("TASK-APPROVE-01")
@Tag("TASK-REJECT-IN-REVIEW-01")
@Tag("TASK-DECIDE-DEADLINE-01")
@Tag("TASK-EDIT-01")
class TaskExceptionHandlerTest {
    private final TaskExceptionHandler handler = new TaskExceptionHandler();

    @Test
    void anAbsentScoreIsRefusedAsAMissingFieldRatherThanAsABrokenProduct() {
        ResponseEntity<ErrorResponse> refused = handler.onScoreMissing(new ApprovalScoreRequiredException());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().code()).isEqualTo("REQUEST_INVALID");
        assertThat(refused.getBody().details()).singleElement().satisfies(violation -> {
            assertThat(violation.field()).isEqualTo("score");
            assertThat(violation.rule()).isEqualTo("REQUIRED");
        });
    }

    @Test
    void aScoreOutsideOneToFiveIsRefusedAsOutOfRange() {
        ResponseEntity<ErrorResponse> refused = handler.onScoreOutOfRange(new ApprovalScoreOutOfRangeException(9));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().details()).singleElement().satisfies(violation -> {
            assertThat(violation.field()).isEqualTo("score");
            assertThat(violation.rule()).isEqualTo("OUT_OF_RANGE");
        });
    }

    @Test
    void aReturnWithNothingToActOnIsRefusedAgainstTheReasonField() {
        ResponseEntity<ErrorResponse> refused = handler.onReworkReasonMissing(new ReworkReasonRequiredException());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().details()).singleElement().satisfies(violation -> {
            assertThat(violation.field()).isEqualTo("reason");
            assertThat(violation.rule()).isEqualTo("REQUIRED");
        });
    }

    @Test
    void aDateThatExpiredWhileNobodyAnsweredIsRefusedWithSomethingToDoAboutIt() {
        ResponseEntity<ErrorResponse> refused = handler.onProposalIsStale(new ProposalIsStaleException());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getBody().code()).isEqualTo("PROPOSAL_IS_STALE");
        assertThat(refused.getBody().message())
                .as("it names the remedy, because a refusal that only refuses leaves somebody stuck")
                .contains("propose another");
    }

    @Test
    void aClosedTaskIsAConflictRatherThanAMalformedRequest() {
        ResponseEntity<ErrorResponse> refused = handler.onTaskIsClosed(new TaskIsClosedException());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().code()).isEqualTo("TASK_IS_CLOSED");
    }

    @Test
    void anEditThatChangesNothingIsRefusedRatherThanAppended() {
        ResponseEntity<ErrorResponse> refused = handler.onNothingChanged(new NothingChangedException());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getBody().code()).isEqualTo("NOTHING_CHANGED");
    }
}
