package com.flowops.process.api;

import com.flowops.process.application.categorise.ProcessCategoryUseCase;
import com.flowops.shared.web.ErrorResponse;
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
@RequestMapping("/api/process-categories")
@Tag(name = "Process categories", description = "Named groupings for runs, so a board of fifty is readable.")
public class ProcessCategoryController {
    private final ProcessCategoryUseCase categories;

    public ProcessCategoryController(ProcessCategoryUseCase categories) {
        this.categories = categories;
    }

    public record NameRequest(@NotBlank @Size(max = 80) String name) {}

    public record FileRunRequest(
            @Schema(description = "Null takes the run out of whatever it was in.") UUID categoryId) {}

    @GetMapping
    @Operation(
            summary = "The groupings in this workspace",
            description = "Requires PROCESS_VIEW_OWN. The count on each is keyed to the grouping and never to a "
                    + "person: CANVAS_00 section 5 permits an aggregate about work and forbids one about "
                    + "anybody doing it.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The groupings, by name"))
    public CategoriesResponse all() {
        return new CategoriesResponse(categories.all(), categories.filings());
    }

    public record CategoriesResponse(List<ProcessCategoryUseCase.Category> categories, Map<UUID, UUID> filings) {}

    @PostMapping
    @Operation(
            summary = "Name a new grouping",
            description = "Requires WORKSPACE_CONFIGURE — a taxonomy everybody reads is a workspace decision "
                    + "rather than one person's. Names are unique per workspace ignoring case and surrounding "
                    + "space, because *Clients* and *clients* are one idea and a second would split the runs "
                    + "between them.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The grouping that now exists"),
        @ApiResponse(
                responseCode = "409",
                description = "CATEGORY_NAME_TAKEN",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, UUID>> create(@Valid @RequestBody NameRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", categories.create(request.name())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename a grouping", description = "Requires WORKSPACE_CONFIGURE. The same name rule.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Renamed"),
        @ApiResponse(responseCode = "404", description = "CATEGORY_NOT_FOUND", content = @Content),
        @ApiResponse(responseCode = "409", description = "CATEGORY_NAME_TAKEN", content = @Content)
    })
    public ResponseEntity<Void> rename(@PathVariable UUID id, @Valid @RequestBody NameRequest request) {
        categories.rename(id, request.name());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Remove a grouping",
            description = "Requires WORKSPACE_CONFIGURE. **Its runs are not touched** — they come home to "
                    + "uncategorised, which is where they started. Removing a way of looking at work must "
                    + "never remove work.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Removed; its runs are uncategorised"),
        @ApiResponse(responseCode = "404", description = "CATEGORY_NOT_FOUND", content = @Content)
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categories.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/runs/{instanceId}")
    @Operation(
            summary = "File a run under a grouping, or take it out of one",
            description = "Requires PROCESS_EDIT_INSTANCE — filing one run is an ordinary edit of that run, "
                    + "which is a different act from shaping the vocabulary everybody reads. A run belongs to "
                    + "at most one grouping: several would put it in three places on a board whose whole "
                    + "purpose is to make fifty runs readable.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Filed, or taken out"),
        @ApiResponse(responseCode = "404", description = "CATEGORY_NOT_FOUND, or no such run here", content = @Content)
    })
    public ResponseEntity<Void> fileRun(@PathVariable UUID instanceId, @RequestBody FileRunRequest request) {
        categories.fileRun(instanceId, Optional.ofNullable(request.categoryId()));
        return ResponseEntity.noContent().build();
    }
}
