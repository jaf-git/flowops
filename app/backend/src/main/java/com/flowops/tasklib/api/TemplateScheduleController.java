package com.flowops.tasklib.api;

import com.flowops.shared.web.ErrorResponse;
import com.flowops.tasklib.api.dto.ScheduleRequest;
import com.flowops.tasklib.api.dto.TemplateScheduleResponse;
import com.flowops.tasklib.application.TemplateScheduleUseCase;
import com.flowops.tasklib.application.exception.ScheduleNotFoundException;
import com.flowops.tasklib.application.exception.TemplateNotFoundException;
import com.flowops.tasklib.domain.exception.IllegalTemplateTransitionException;
import com.flowops.tasklib.domain.exception.InvalidRecurrenceException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task-templates/{templateId}/schedules")
@Tag(name = "Task template schedules", description = "A template that raises its own work, on a cadence")
public class TemplateScheduleController {
    private final TemplateScheduleUseCase schedules;

    public TemplateScheduleController(TemplateScheduleUseCase schedules) {
        this.schedules = schedules;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_VIEW')")
    @Operation(
            summary = "Every schedule on this template",
            description = "TASKLIB-USE-TEMPLATE-01, the recurring half. Requires TASK_TEMPLATE_VIEW. Running "
                    + "schedules first, then paused ones — a paused schedule is history the screen keeps rather "
                    + "than something somebody is looking for. `nextOccurrence` is computed on every read and is "
                    + "null while a schedule is paused, so it can never be a date that will not happen. A "
                    + "template that does not exist answers 404 rather than an empty list, because *nothing is "
                    + "scheduled* is the most reassuring wrong answer this route could give.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Its schedules, running first"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_TEMPLATE_VIEW",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "No such template", content = @Content)
    })
    public ResponseEntity<List<TemplateScheduleResponse>> forTemplate(@PathVariable UUID templateId) {
        return ResponseEntity.ok(schedules.forTemplate(templateId).stream()
                .map(TemplateScheduleResponse::of)
                .toList());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TASK_CREATE')")
    @Operation(
            summary = "Set this template to raise work on a cadence",
            description = "TASKLIB-USE-TEMPLATE-01, the recurring half. Requires TASK_CREATE, because the "
                    + "result is a standing instruction to create tasks and that is TASK's permission to give. "
                    + "\n\n"
                    + "**The schedule holds no authority of its own.** Every task it raises is created as the "
                    + "person who set it up, through TASK's own create path, so the reporting-line check runs "
                    + "against them at *every* occurrence rather than once here. A schedule therefore stops "
                    + "raising work the day its author loses the right to direct that assignee, and the screen "
                    + "says so rather than the tasks quietly continuing.\n\n"
                    + "**Only an approved template may be scheduled**, asked now rather than left for the first "
                    + "occurrence — a schedule on a draft would look correct until the 1st of next month and "
                    + "then refuse into a log nobody reads.\n\n"
                    + "A monthly schedule on the 31st lands on the last day of a shorter month rather than "
                    + "being skipped, because *the last day* is what somebody picking 31 means.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The schedule, and when it next fires"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID: no assignee, or a cadence with no day",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_CREATE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "No such template", content = @Content),
        @ApiResponse(
                responseCode = "409",
                description = "TEMPLATE_STATE_REFUSES: it is not an approved template",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TemplateScheduleResponse> set(
            @PathVariable UUID templateId, @Valid @RequestBody ScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TemplateScheduleResponse.of(
                        schedules.set(templateId, request.assigneeId(), request.toRecurrence())));
    }

    @PostMapping("/{scheduleId}/pause")
    @PreAuthorize("hasAuthority('TASK_CREATE')")
    @Operation(
            summary = "Stop a schedule raising work",
            description = "The row is kept and stops firing, for the reason nothing in this feature is "
                    + "deleted: the occurrences it has already raised are the provenance of real tasks, and "
                    + "removing it would orphan every one of them. Pausing twice is refused rather than being "
                    + "a harmless repeat.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paused, and still readable"),
        @ApiResponse(responseCode = "404", description = "No such schedule", content = @Content),
        @ApiResponse(
                responseCode = "409",
                description = "TEMPLATE_STATE_REFUSES: it is already paused",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TemplateScheduleResponse> pause(
            @PathVariable UUID templateId, @PathVariable UUID scheduleId) {
        return ResponseEntity.ok(TemplateScheduleResponse.of(schedules.pause(scheduleId)));
    }

    @PostMapping("/{scheduleId}/resume")
    @PreAuthorize("hasAuthority('TASK_CREATE')")
    @Operation(
            summary = "Start it raising work again",
            description = "**From the next occurrence, never catching up on the ones it missed.** A schedule "
                    + "paused over a quarter does not raise three months of work on the day it resumes. It "
                    + "re-asks whether the template is still approved, because the months it spent paused are "
                    + "exactly when somebody is most likely to have retired it underneath.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Running again, with its next occurrence"),
        @ApiResponse(responseCode = "404", description = "No such schedule", content = @Content),
        @ApiResponse(
                responseCode = "409",
                description =
                        "TEMPLATE_STATE_REFUSES: it is already running, or the template is no longer " + "approved",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TemplateScheduleResponse> resume(
            @PathVariable UUID templateId, @PathVariable UUID scheduleId) {
        return ResponseEntity.ok(TemplateScheduleResponse.of(schedules.resume(scheduleId)));
    }

    @ExceptionHandler(ScheduleNotFoundException.class)
    ResponseEntity<ErrorResponse> scheduleNotFound(ScheduleNotFoundException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("SCHEDULE_NOT_FOUND", failure.getMessage()));
    }

    @ExceptionHandler(TemplateNotFoundException.class)
    ResponseEntity<ErrorResponse> templateNotFound(TemplateNotFoundException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("TEMPLATE_NOT_FOUND", failure.getMessage()));
    }

    @ExceptionHandler(IllegalTemplateTransitionException.class)
    ResponseEntity<ErrorResponse> refusedByItsState(IllegalTemplateTransitionException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("TEMPLATE_STATE_REFUSES", failure.getMessage()));
    }

    @ExceptionHandler(InvalidRecurrenceException.class)
    ResponseEntity<ErrorResponse> invalidRecurrence(InvalidRecurrenceException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        failure.getMessage(),
                        java.util.List.of(new ErrorResponse.FieldViolation(failure.field(), "REQUIRED"))));
    }
}
