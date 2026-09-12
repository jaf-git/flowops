package com.flowops.task.api.dto;

import com.flowops.task.domain.enums.TaskPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateTaskRequest(
        @NotBlank @Size(max = 200) @Schema(example = "Draft the quarterly supplier review") String title,
        @Size(max = 4000) String description,
        @NotNull UUID assigneeId,
        @Schema(example = "2026-09-01T09:00:00Z") Instant deadline,
        TaskPriority priority,
        UUID templateId,
        @Pattern(regexp = "TASK|TICKET") String kind) {
    @AssertTrue(message = "a ticket is unfiled work and cannot come from a template")
    public boolean isKindConsistentWithProvenance() {
        return !"TICKET".equals(kind) || templateId == null;
    }
}
