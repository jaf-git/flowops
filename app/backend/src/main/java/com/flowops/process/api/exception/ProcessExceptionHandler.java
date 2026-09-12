package com.flowops.process.api.exception;

import com.flowops.process.application.categorise.CategoryNameTakenException;
import com.flowops.process.application.categorise.CategoryNotFoundException;
import com.flowops.process.application.shared.exception.AddTaskRequestNotOneOfTwoException;
import com.flowops.process.application.shared.exception.InstanceNotFoundException;
import com.flowops.process.application.shared.exception.NotAuthenticatedException;
import com.flowops.process.application.shared.exception.NotTheAuthorException;
import com.flowops.process.application.shared.exception.StepTemplateUnavailableException;
import com.flowops.process.application.shared.exception.TaskAlreadyInAProcessException;
import com.flowops.process.application.shared.exception.TaskNotAttachableException;
import com.flowops.process.application.shared.exception.TemplateNameTakenException;
import com.flowops.process.application.shared.exception.TemplateNotFoundException;
import com.flowops.process.domain.exception.AbandonReasonRequiredException;
import com.flowops.process.domain.exception.ClosureNoteRequiredException;
import com.flowops.process.domain.exception.ConditionNeedsAnOptionalStepException;
import com.flowops.process.domain.exception.CrossTemplateEdgeException;
import com.flowops.process.domain.exception.CycleWouldFormException;
import com.flowops.process.domain.exception.IllegalStepTransitionException;
import com.flowops.process.domain.exception.InstanceNeedsATaskException;
import com.flowops.process.domain.exception.InstanceNotRunningException;
import com.flowops.process.domain.exception.InstanceStillRunningException;
import com.flowops.process.domain.exception.ProcessOwnerNotActiveException;
import com.flowops.process.domain.exception.StepDoesNotApplyOptionallyException;
import com.flowops.process.domain.exception.StepNeedsTaskTemplateException;
import com.flowops.process.domain.exception.StepNotAwaitingADecisionException;
import com.flowops.process.domain.exception.StepNotReachableException;
import com.flowops.process.domain.exception.StepWouldBeStrandedException;
import com.flowops.process.domain.exception.TemplateIsRetiredException;
import com.flowops.process.domain.exception.TemplateNameRequiredException;
import com.flowops.process.domain.exception.TemplateNeedsAStepException;
import com.flowops.process.domain.exception.UnknownOwnerRoleException;
import com.flowops.process.domain.exception.UnknownStepException;
import com.flowops.process.domain.model.StepId;
import com.flowops.shared.web.ErrorResponse;
import com.flowops.task.application.shared.exception.AssigneeNotActiveException;
import com.flowops.task.application.shared.exception.AssigneeOutOfScopeException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.flowops.process")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProcessExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessExceptionHandler.class);

    @ExceptionHandler(CategoryNameTakenException.class)
    ResponseEntity<ErrorResponse> onCategoryNameTaken(CategoryNameTakenException refusal) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CATEGORY_NAME_TAKEN", refusal.getMessage()));
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    ResponseEntity<ErrorResponse> onCategoryNotFound(CategoryNotFoundException refusal) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("CATEGORY_NOT_FOUND", refusal.getMessage()));
    }

    @ExceptionHandler(NotAuthenticatedException.class)
    public ResponseEntity<ErrorResponse> onNoSession(NotAuthenticatedException failure) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("NOT_AUTHENTICATED", "Sign in to continue."));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> onDenied(AuthorizationDeniedException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("NOT_PERMITTED", "You do not have permission to do that."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> onUnreadableBody(HttpMessageNotReadableException failure) {
        LOG.info("process request rejected: the body could not be read");
        return ResponseEntity.badRequest().body(ErrorResponse.of("MALFORMED_BODY", "The request could not be read."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> onRefusedWrite(DataIntegrityViolationException failure) {
        LOG.info(
                "process write refused by the database: {}",
                failure.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "WRITE_REFUSED", "Somebody else changed something at the same moment. Try again."));
    }

    @ExceptionHandler(TemplateNotFoundException.class)
    public ResponseEntity<ErrorResponse> onMissing(TemplateNotFoundException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("TEMPLATE_NOT_FOUND", "There is no such process."));
    }

    @ExceptionHandler(InstanceNotFoundException.class)
    public ResponseEntity<ErrorResponse> onInstanceMissing(InstanceNotFoundException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("INSTANCE_NOT_FOUND", "There is no such process run."));
    }

    @ExceptionHandler(ProcessOwnerNotActiveException.class)
    public ResponseEntity<ErrorResponse> onOwnerNotActive(ProcessOwnerNotActiveException failure) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of(
                        "PROCESS_OWNER_NOT_ACTIVE",
                        "A process needs somebody who can steer it.",
                        List.of(new ErrorResponse.FieldViolation("processOwnerId", "NOT_ACTIVE"))));
    }

    @ExceptionHandler(NotTheAuthorException.class)
    public ResponseEntity<ErrorResponse> onNotTheAuthor(NotTheAuthorException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(
                        "NOT_THE_AUTHOR",
                        "That process is somebody else's to change. Ask whoever wrote it, or the owner."));
    }

    @ExceptionHandler(TemplateNameTakenException.class)
    public ResponseEntity<ErrorResponse> onNameTaken(TemplateNameTakenException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "TEMPLATE_NAME_TAKEN",
                        "A process with that name is already in use.",
                        List.of(new ErrorResponse.FieldViolation("name", "TAKEN"))));
    }

    @ExceptionHandler(TemplateNameRequiredException.class)
    public ResponseEntity<ErrorResponse> onNameMissing(TemplateNameRequiredException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "Give the process a name.",
                        List.of(new ErrorResponse.FieldViolation("name", "REQUIRED"))));
    }

    @ExceptionHandler(TemplateNeedsAStepException.class)
    public ResponseEntity<ErrorResponse> onNoSteps(TemplateNeedsAStepException failure) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of(
                        "TEMPLATE_NEEDS_A_STEP",
                        "A process with no work in it is not a process.",
                        List.of(new ErrorResponse.FieldViolation("steps", "REQUIRED"))));
    }

    @ExceptionHandler(StepNeedsTaskTemplateException.class)
    public ResponseEntity<ErrorResponse> onStepWithoutWork(StepNeedsTaskTemplateException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "STEP_TASK_TEMPLATE_REQUIRED",
                        "Step " + (failure.position() + 1) + " names no task template.",
                        List.of(new ErrorResponse.FieldViolation(
                                "steps[" + failure.position() + "].taskTemplateId", "REQUIRED"))));
    }

    @ExceptionHandler(StepTemplateUnavailableException.class)
    public ResponseEntity<ErrorResponse> onUnavailableStepTemplate(StepTemplateUnavailableException failure) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of(
                        "STEP_TEMPLATE_UNAVAILABLE",
                        "Step " + (failure.position() + 1) + " references a task template that cannot be read.",
                        List.of(new ErrorResponse.FieldViolation(
                                "steps[" + failure.position() + "].taskTemplateId", "UNAVAILABLE"))));
    }

    @ExceptionHandler(CycleWouldFormException.class)
    public ResponseEntity<ErrorResponse> onCycle(CycleWouldFormException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "GRAPH_CYCLE",
                        "Those steps would end up waiting for each other.",
                        violations(failure.cycle(), "IN_CYCLE")));
    }

    @ExceptionHandler(StepWouldBeStrandedException.class)
    public ResponseEntity<ErrorResponse> onStranded(StepWouldBeStrandedException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "GRAPH_STEP_STRANDED",
                        "That would leave a step nothing ever reaches.",
                        violations(List.copyOf(failure.stranded()), "STRANDED")));
    }

    @ExceptionHandler(StepDoesNotApplyOptionallyException.class)
    public ResponseEntity<ErrorResponse> onNotOptional(StepDoesNotApplyOptionallyException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("STEP_NOT_OPTIONAL", failure.getMessage()));
    }

    @ExceptionHandler(StepNotAwaitingADecisionException.class)
    public ResponseEntity<ErrorResponse> onNotAwaiting(StepNotAwaitingADecisionException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("STEP_NOT_AWAITING_DECISION", failure.getMessage()));
    }

    @ExceptionHandler(StepNotReachableException.class)
    public ResponseEntity<ErrorResponse> onNotReachable(StepNotReachableException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "STEP_NOT_REACHABLE",
                        "That step is still waiting on others.",
                        violations(List.copyOf(failure.unmet()), "NOT_CLOSED")));
    }

    @ExceptionHandler(IllegalStepTransitionException.class)
    public ResponseEntity<ErrorResponse> onIllegalStepMove(IllegalStepTransitionException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "ILLEGAL_STEP_TRANSITION",
                        "That step is already " + failure.from().name().toLowerCase(java.util.Locale.ROOT) + ".",
                        List.of(new ErrorResponse.FieldViolation(
                                "condition", failure.from().name()))));
    }

    @ExceptionHandler(CrossTemplateEdgeException.class)
    public ResponseEntity<ErrorResponse> onCrossTemplate(CrossTemplateEdgeException failure) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of("CROSS_TEMPLATE_EDGE", "A dependency joins two steps of the same process."));
    }

    @ExceptionHandler(UnknownStepException.class)
    public ResponseEntity<ErrorResponse> onUnknownStep(UnknownStepException failure) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of("UNKNOWN_STEP", "That step is not part of this process."));
    }

    @ExceptionHandler(TaskNotAttachableException.class)
    public ResponseEntity<ErrorResponse> onTaskNotAttachable(TaskNotAttachableException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("TASK_NOT_FOUND", "There is no such task."));
    }

    @ExceptionHandler(TaskAlreadyInAProcessException.class)
    public ResponseEntity<ErrorResponse> onTaskAlreadyInAProcess(TaskAlreadyInAProcessException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "TASK_ALREADY_IN_A_PROCESS", "That task is already part of another process run."));
    }

    @ExceptionHandler(InstanceNeedsATaskException.class)
    public ResponseEntity<ErrorResponse> onInstanceNeedsATask(InstanceNeedsATaskException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "INSTANCE_NEEDS_A_TASK", "A run with no work in it is not a run. This is its last task."));
    }

    @ExceptionHandler(InstanceStillRunningException.class)
    public ResponseEntity<ErrorResponse> onInstanceStillRunning(InstanceStillRunningException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "INSTANCE_STILL_RUNNING",
                        "This run is still going, so it cannot be put away. Finish or stop it first."));
    }

    @ExceptionHandler(InstanceNotRunningException.class)
    public ResponseEntity<ErrorResponse> onInstanceNotRunning(InstanceNotRunningException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("INSTANCE_NOT_RUNNING", "This run has finished. Start another one."));
    }

    @ExceptionHandler(AddTaskRequestNotOneOfTwoException.class)
    public ResponseEntity<ErrorResponse> onAddTaskRequestNotOneOfTwo(AddTaskRequestNotOneOfTwoException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID", "Choose a task that already exists, or write a new one — not both."));
    }

    @ExceptionHandler(AssigneeNotActiveException.class)
    public ResponseEntity<ErrorResponse> onAssigneeNotActive(AssigneeNotActiveException failure) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of("ASSIGNEE_NOT_ACTIVE", "That person cannot be given work."));
    }

    @ExceptionHandler(AssigneeOutOfScopeException.class)
    public ResponseEntity<ErrorResponse> onAssigneeOutOfScope(AssigneeOutOfScopeException failure) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of("ASSIGNEE_OUT_OF_SCOPE", "That person is not yours to direct."));
    }

    private static List<ErrorResponse.FieldViolation> violations(List<StepId> steps, String rule) {
        List<ErrorResponse.FieldViolation> details = new ArrayList<>();
        for (StepId step : steps) {
            details.add(new ErrorResponse.FieldViolation(step.value().toString(), rule));
        }
        return details;
    }

    @ExceptionHandler(TemplateIsRetiredException.class)
    public ResponseEntity<ErrorResponse> onTemplateRetired(TemplateIsRetiredException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "TEMPLATE_IS_RETIRED",
                        "This template has been retired. Runs already started from it are unaffected."));
    }

    @ExceptionHandler(ConditionNeedsAnOptionalStepException.class)
    public ResponseEntity<ErrorResponse> onConditionWithoutOptional(ConditionNeedsAnOptionalStepException failure) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of(
                        "CONDITION_NEEDS_AN_OPTIONAL_STEP",
                        "A condition only makes sense on a step that is marked optional.",
                        List.of(new ErrorResponse.FieldViolation("conditionNote", "NEEDS_OPTIONAL"))));
    }

    @ExceptionHandler(UnknownOwnerRoleException.class)
    public ResponseEntity<ErrorResponse> onUnknownOwnerRole(UnknownOwnerRoleException failure) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of(
                        "UNKNOWN_OWNER_ROLE",
                        "That is not one of this workspace's roles.",
                        List.of(new ErrorResponse.FieldViolation("ownerRole", "UNKNOWN"))));
    }

    @ExceptionHandler(AbandonReasonRequiredException.class)
    public ResponseEntity<ErrorResponse> onAbandonReasonMissing(AbandonReasonRequiredException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "Say why the run is being stopped.",
                        List.of(new ErrorResponse.FieldViolation("reason", "REQUIRED"))));
    }

    @ExceptionHandler(ClosureNoteRequiredException.class)
    public ResponseEntity<ErrorResponse> onClosureNoteMissing(ClosureNoteRequiredException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "Say why the run is finished while work is still open on it.",
                        List.of(new ErrorResponse.FieldViolation("note", "REQUIRED"))));
    }
}
