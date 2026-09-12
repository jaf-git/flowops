package com.flowops.task.api;

import com.flowops.shared.web.ErrorResponse;
import com.flowops.task.application.categorise.TaskCategoryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task-categories")
@Tag(name = "Task categories", description = "Named groupings for tasks, so a queue of forty can be narrowed.")
public class TaskCategoryController {
    private final TaskCategoryUseCase categories;

    public TaskCategoryController(TaskCategoryUseCase categories) {
        this.categories = categories;
    }

    public record NameRequest(@NotBlank @Size(max = 80) @Schema(example = "Aurora Coffee") String name) {}

    public record FileTaskRequest(
            @Schema(description = "Null takes the task out of whatever it was in.") UUID categoryId) {}

    public record CategoriesResponse(List<TaskCategoryUseCase.Category> categories, Map<UUID, UUID> filings) {}

    @GetMapping
    @Operation(
            summary = "The groupings in this workspace",
            description = "TASK-CATEGORISE-TASKS-01. Requires TASK_VIEW_OWN. The count on each is keyed to the "
                    + "grouping and never to a person: CANVAS_00 section 5 permits an aggregate about work and "
                    + "forbids one about anybody doing it.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The groupings, by name, with what is filed where"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_VIEW_OWN",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CategoriesResponse all() {
        return new CategoriesResponse(categories.all(), categories.filings());
    }

    @PostMapping
    @Operation(
            summary = "Name a new grouping",
            description = "TASK-CATEGORISE-TASKS-01. Requires WORKSPACE_CONFIGURE — a vocabulary everybody reads "
                    + "is a workspace decision rather than one person's. Names are unique per workspace ignoring "
                    + "case and surrounding space, because *Clients* and *clients* are one idea and a second "
                    + "would split the work between them.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The grouping that now exists"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold WORKSPACE_CONFIGURE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "CATEGORY_NAME_TAKEN",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, UUID>> create(@Valid @RequestBody NameRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", categories.create(request.name())));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Rename a grouping",
            description = "TASK-CATEGORISE-TASKS-01. Requires WORKSPACE_CONFIGURE. The same name rule, so a "
                    + "rename cannot collide either.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Renamed"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(responseCode = "403", description = "NOT_PERMITTED", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "CATEGORY_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "CATEGORY_NAME_TAKEN",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> rename(@PathVariable UUID id, @Valid @RequestBody NameRequest request) {
        categories.rename(id, request.name());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Remove a grouping",
            description = "TASK-CATEGORISE-TASKS-01. Requires WORKSPACE_CONFIGURE. **Its tasks are not touched** "
                    + "— they come home to uncategorised, which is where they started. Removing a way of looking "
                    + "at work must never remove work.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Removed; its tasks are uncategorised"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(responseCode = "403", description = "NOT_PERMITTED", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "CATEGORY_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categories.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/tasks/{taskId}")
    @Operation(
            summary = "File a task under a grouping, or take it out of one",
            description = "TASK-CATEGORISE-TASKS-01. Requires TASK_EDIT — filing one task is an ordinary edit of "
                    + "that task, which is a different act from shaping the vocabulary everybody reads. A task "
                    + "belongs to at most one grouping: several would count it in three section headers, and a "
                    + "header that disagrees with its rows is the first thing anybody notices. A grouping that "
                    + "does not exist here is refused rather than reconciled, so a mistyped identifier changes "
                    + "nothing instead of clearing whatever the task was already in.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Filed, or taken out"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_EDIT",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "CATEGORY_NOT_FOUND, or no such task here",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> fileTask(@PathVariable UUID taskId, @RequestBody FileTaskRequest request) {
        categories.fileTask(taskId, Optional.ofNullable(request.categoryId()));
        return ResponseEntity.noContent().build();
    }
}
