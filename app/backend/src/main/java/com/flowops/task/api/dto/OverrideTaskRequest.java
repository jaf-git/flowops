package com.flowops.task.api.dto;

import com.flowops.task.domain.enums.TaskState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OverrideTaskRequest(
        @NotNull
                @Schema(
                        description = "The state to force. Any of the seven, including Closed — and out of"
                                + " Closed, which no other route offers.",
                        example = "CLOSED")
                TaskState targetState,
        @NotBlank
                @Size(max = 2000)
                @Schema(
                        description = "Why the usual rules are being set aside. Stays on the record"
                                + " permanently and marks the transition as an override.",
                        example = "Comanda a fost anulată de client; sarcina nu mai are obiect.")
                String reason) {}
