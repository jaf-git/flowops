package com.flowops.task.api.exception;

import com.flowops.shared.web.ErrorResponse;
import com.flowops.task.application.categorise.CategoryNameTakenException;
import com.flowops.task.application.categorise.CategoryNotFoundException;
import com.flowops.task.application.shared.exception.AssigneeNotActiveException;
import com.flowops.task.application.shared.exception.AssigneeOutOfScopeException;
import com.flowops.task.application.shared.exception.NotAuthenticatedException;
import com.flowops.task.application.shared.exception.NotTheAssigneeException;
import com.flowops.task.application.shared.exception.NotTheCreatorException;
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.exception.TaskOutOfScopeException;
import com.flowops.task.domain.exception.ApprovalScoreOutOfRangeException;
import com.flowops.task.domain.exception.ApprovalScoreRequiredException;
import com.flowops.task.domain.exception.BlockReasonRequiredException;
import com.flowops.task.domain.exception.CannotReviewOwnWorkException;
import com.flowops.task.domain.exception.ChecklistTextRequiredException;
import com.flowops.task.domain.exception.CommentBodyRequiredException;
import com.flowops.task.domain.exception.CompletionNoteRequiredException;
import com.flowops.task.domain.exception.DeadlineInThePastException;
import com.flowops.task.domain.exception.DeadlineRequiredException;
import com.flowops.task.domain.exception.DeadlineRequiredToStartException;
import com.flowops.task.domain.exception.DeclineReasonRequiredException;
import com.flowops.task.domain.exception.IllegalTransitionException;
import com.flowops.task.domain.exception.LinkSchemeNotAllowedException;
import com.flowops.task.domain.exception.LinkUrlRequiredException;
import com.flowops.task.domain.exception.NoOpenProposalException;
import com.flowops.task.domain.exception.NothingChangedException;
import com.flowops.task.domain.exception.OverrideReasonRequiredException;
import com.flowops.task.domain.exception.ProposalAlreadyOpenException;
import com.flowops.task.domain.exception.ProposalIsStaleException;
import com.flowops.task.domain.exception.ProposalReasonRequiredException;
import com.flowops.task.domain.exception.ReassignReasonRequiredException;
import com.flowops.task.domain.exception.RejectReasonRequiredException;
import com.flowops.task.domain.exception.ReworkReasonRequiredException;
import com.flowops.task.domain.exception.TaskIsClosedException;
import com.flowops.task.domain.exception.TaskTitleRequiredException;
import com.flowops.task.domain.exception.UseAProposalInsteadException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.flowops.task")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TaskExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TaskExceptionHandler.class);

    @ExceptionHandler(NotAuthenticatedException.class)
    public ResponseEntity<ErrorResponse> onNoSession(NotAuthenticatedException failure) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("NOT_AUTHENTICATED", "Sign in to continue."));
    }

    @ExceptionHandler(TaskTitleRequiredException.class)
    public ResponseEntity<ErrorResponse> onTitleMissing(TaskTitleRequiredException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "Give the task a title.",
                        List.of(new ErrorResponse.FieldViolation("title", "REQUIRED"))));
    }

    @ExceptionHandler(DeadlineRequiredException.class)
    public ResponseEntity<ErrorResponse> onDeadlineMissing(DeadlineRequiredException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "Give the task a deadline.",
                        List.of(new ErrorResponse.FieldViolation("deadline", "REQUIRED"))));
    }

    @ExceptionHandler(BlockReasonRequiredException.class)
    public ResponseEntity<ErrorResponse> onBlockReasonMissing(BlockReasonRequiredException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "Say what the work is waiting on.",
                        List.of(new ErrorResponse.FieldViolation("reason", "REQUIRED"))));
    }

    @ExceptionHandler(CompletionNoteRequiredException.class)
    public ResponseEntity<ErrorResponse> onCompletionNoteMissing(CompletionNoteRequiredException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "Say what you did, so somebody can judge the result.",
                        List.of(new ErrorResponse.FieldViolation("note", "REQUIRED"))));
    }

    @ExceptionHandler(DeadlineInThePastException.class)
    public ResponseEntity<ErrorResponse> onDeadlinePassed(DeadlineInThePastException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "DEADLINE_IN_THE_PAST",
                        "That deadline has already passed. Choose one in the future.",
                        List.of(new ErrorResponse.FieldViolation("deadline", "IN_THE_PAST"))));
    }

    @ExceptionHandler(AssigneeNotActiveException.class)
    public ResponseEntity<ErrorResponse> onAssigneeInactive(AssigneeNotActiveException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "ASSIGNEE_NOT_ACTIVE",
                        "That person no longer has access. Choose somebody else.",
                        List.of(new ErrorResponse.FieldViolation("assigneeId", "NOT_ACTIVE"))));
    }

    @ExceptionHandler(AssigneeOutOfScopeException.class)
    public ResponseEntity<ErrorResponse> onAssigneeOutOfScope(AssigneeOutOfScopeException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "ASSIGNEE_OUT_OF_SCOPE",
                        "You can only give work to people who report to you.",
                        List.of(new ErrorResponse.FieldViolation("assigneeId", "OUT_OF_SCOPE"))));
    }

    @ExceptionHandler(NotTheAssigneeException.class)
    public ResponseEntity<ErrorResponse> onNotTheAssignee(NotTheAssigneeException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(
                        "NOT_THE_ASSIGNEE",
                        "This work is somebody else's to accept. If it should be yours, ask for it to be "
                                + "reassigned."));
    }

    @ExceptionHandler(CannotReviewOwnWorkException.class)
    public ResponseEntity<ErrorResponse> onSelfReview(CannotReviewOwnWorkException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(
                        "CANNOT_REVIEW_OWN_WORK", "This is your own work, so somebody else has to judge it."));
    }

    @ExceptionHandler(TaskOutOfScopeException.class)
    public ResponseEntity<ErrorResponse> onTaskOutOfScope(TaskOutOfScopeException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(
                        "TASK_OUT_OF_SCOPE",
                        "This task belongs to somebody outside your team. Their own manager or the owner can act on "
                                + "it."));
    }

    @ExceptionHandler(ApprovalScoreRequiredException.class)
    public ResponseEntity<ErrorResponse> onScoreMissing(ApprovalScoreRequiredException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "Say how the work was, from one to five.",
                        List.of(new ErrorResponse.FieldViolation("score", "REQUIRED"))));
    }

    @ExceptionHandler(ApprovalScoreOutOfRangeException.class)
    public ResponseEntity<ErrorResponse> onScoreOutOfRange(ApprovalScoreOutOfRangeException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "A score runs from one to five.",
                        List.of(new ErrorResponse.FieldViolation("score", "OUT_OF_RANGE"))));
    }

    @ExceptionHandler(ReworkReasonRequiredException.class)
    public ResponseEntity<ErrorResponse> onReworkReasonMissing(ReworkReasonRequiredException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "Say what needs to change, so the work can be finished rather than guessed at.",
                        List.of(new ErrorResponse.FieldViolation("reason", "REQUIRED"))));
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ErrorResponse> onTaskMissing(TaskNotFoundException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("TASK_NOT_FOUND", "That task no longer exists."));
    }

    @ExceptionHandler(CategoryNameTakenException.class)
    public ResponseEntity<ErrorResponse> onCategoryNameTaken(CategoryNameTakenException refusal) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CATEGORY_NAME_TAKEN", refusal.getMessage()));
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ErrorResponse> onCategoryNotFound(CategoryNotFoundException refusal) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("CATEGORY_NOT_FOUND", refusal.getMessage()));
    }

    @ExceptionHandler(IllegalTransitionException.class)
    public ResponseEntity<ErrorResponse> onIllegalTransition(IllegalTransitionException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "ILLEGAL_TRANSITION",
                        "This task has moved on since your screen was loaded.",
                        List.of(new ErrorResponse.FieldViolation(
                                "state", failure.from().name()))));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> onAuthorizationDenied(AuthorizationDeniedException failure) {
        LOG.info("task action refused: the caller lacks the required permission");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("NOT_PERMITTED", "You may not perform this action."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> onUnreadableBody(HttpMessageNotReadableException failure) {
        LOG.info("task request rejected: the body could not be read");
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("MALFORMED_BODY", "That request body could not be read."));
    }

    @ExceptionHandler(RejectReasonRequiredException.class)
    public ResponseEntity<ErrorResponse> onRejectReasonMissing(RejectReasonRequiredException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "Say why this is not yours to do, so it can go to the right person.",
                        List.of(new ErrorResponse.FieldViolation("reason", "REQUIRED"))));
    }

    @ExceptionHandler(ProposalReasonRequiredException.class)
    public ResponseEntity<ErrorResponse> onProposalReasonMissing(ProposalReasonRequiredException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "Say why the current date cannot be met.",
                        List.of(new ErrorResponse.FieldViolation("reason", "REQUIRED"))));
    }

    @ExceptionHandler(DeclineReasonRequiredException.class)
    public ResponseEntity<ErrorResponse> onDeclineReasonMissing(DeclineReasonRequiredException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "Say why the date cannot move, so they know where they stand.",
                        List.of(new ErrorResponse.FieldViolation("reason", "REQUIRED"))));
    }

    @ExceptionHandler(NotTheCreatorException.class)
    public ResponseEntity<ErrorResponse> onNotTheCreator(NotTheCreatorException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("NOT_THE_CREATOR", "Only the person who assigned this work can change it."));
    }

    @ExceptionHandler(ProposalAlreadyOpenException.class)
    public ResponseEntity<ErrorResponse> onProposalAlreadyOpen(ProposalAlreadyOpenException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "PROPOSAL_ALREADY_OPEN",
                        "You have already asked for a different date on this task, and nobody has answered yet.",
                        List.of(new ErrorResponse.FieldViolation(
                                "proposedDeadline",
                                failure.openProposalDeadline().toString()))));
    }

    @ExceptionHandler(NoOpenProposalException.class)
    public ResponseEntity<ErrorResponse> onNoOpenProposal(NoOpenProposalException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "NO_OPEN_PROPOSAL", "There is no deadline request waiting for an answer on this task."));
    }

    @ExceptionHandler(ProposalIsStaleException.class)
    public ResponseEntity<ErrorResponse> onProposalIsStale(ProposalIsStaleException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "PROPOSAL_IS_STALE",
                        "The date being asked for has already passed. Ask them to propose another."));
    }

    @ExceptionHandler(DeadlineRequiredToStartException.class)
    public ResponseEntity<ErrorResponse> deadlineRequiredToStart(DeadlineRequiredToStartException failure) {
        LOG.debug("start refused: no deadline", failure);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("DEADLINE_REQUIRED_TO_START", "Set a date on this work before you start it."));
    }

    @ExceptionHandler(UseAProposalInsteadException.class)
    public ResponseEntity<ErrorResponse> useAProposalInstead(UseAProposalInsteadException failure) {
        LOG.debug("set deadline refused: work has begun", failure);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "USE_A_PROPOSAL_INSTEAD",
                        "Work has started, so ask for a different date instead of setting one."));
    }

    @ExceptionHandler(LinkSchemeNotAllowedException.class)
    public ResponseEntity<ErrorResponse> linkSchemeNotAllowed(LinkSchemeNotAllowedException failure) {
        LOG.debug("link refused: scheme not allowed", failure);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "LINK_SCHEME_NOT_ALLOWED",
                        "Only web addresses beginning http:// or https:// can be attached.",
                        List.of(new ErrorResponse.FieldViolation("url", "SCHEME_NOT_ALLOWED"))));
    }

    @ExceptionHandler(LinkUrlRequiredException.class)
    public ResponseEntity<ErrorResponse> linkUrlRequired(LinkUrlRequiredException failure) {
        LOG.debug("link refused: no address", failure);
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "A link needs an address.",
                        List.of(new ErrorResponse.FieldViolation("url", "REQUIRED"))));
    }

    @ExceptionHandler(ChecklistTextRequiredException.class)
    public ResponseEntity<ErrorResponse> checklistTextRequired(ChecklistTextRequiredException failure) {
        LOG.debug("step refused: no text", failure);
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "A step says what to do.",
                        List.of(new ErrorResponse.FieldViolation("text", "REQUIRED"))));
    }

    @ExceptionHandler(TaskIsClosedException.class)
    public ResponseEntity<ErrorResponse> onTaskIsClosed(TaskIsClosedException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("TASK_IS_CLOSED", "This task is closed, and a closed task is a record."));
    }

    @ExceptionHandler(NothingChangedException.class)
    public ResponseEntity<ErrorResponse> onNothingChanged(NothingChangedException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "NOTHING_CHANGED", "Nothing here is different from what the task already says."));
    }

    @ExceptionHandler(ReassignReasonRequiredException.class)
    public ResponseEntity<ErrorResponse> onReassignReasonMissing(ReassignReasonRequiredException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "Say why the work is moving. The person who had it will read this.",
                        List.of(new ErrorResponse.FieldViolation("reason", "REQUIRED"))));
    }

    @ExceptionHandler(OverrideReasonRequiredException.class)
    public ResponseEntity<ErrorResponse> onOverrideReasonMissing(OverrideReasonRequiredException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "Say why the usual rules are being set aside. This stays on the record.",
                        List.of(new ErrorResponse.FieldViolation("reason", "REQUIRED"))));
    }

    @ExceptionHandler(CommentBodyRequiredException.class)
    public ResponseEntity<ErrorResponse> onCommentBodyMissing(CommentBodyRequiredException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "Write something before posting.",
                        List.of(new ErrorResponse.FieldViolation("body", "REQUIRED"))));
    }
}
