package com.flowops.process.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AddTaskRequest(
        @Schema(
                        description = "A task that already exists, to be put into this run keeping its assignee, "
                                + "deadline and state. Supply this or the new-task fields, never both.")
                UUID taskId,
        @Schema(description = "The title of a task to write straight into this run.") @Size(max = 200) String title,
        @Schema(description = "What is being asked for.") @Size(max = 4000) String description,
        @Schema(description = "Who will do it. Required when writing a new task.") UUID assigneeId,
        @Schema(description = "When it is due. Optional; the assignee may set it after accepting.") Instant deadline,
        @Schema(description = "How urgent it is. Defaults to NORMAL.") String priority,
        @Schema(description = "The steps this one runs after. Empty means it may begin at once.")
                List<UUID> dependsOnStepIds) {}
