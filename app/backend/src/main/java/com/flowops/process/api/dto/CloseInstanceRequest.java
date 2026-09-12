package com.flowops.process.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CloseInstanceRequest(
        @NotBlank
                @Size(max = 2000)
                @Schema(
                        description = "Why the run is finished with steps still open. Everybody who held a task"
                                + " in it will read this.",
                        example = "Clientul a semnat la ședință; ultimii doi pași nu mai sunt necesari.")
                String note) {}
