package com.flowops.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record UnblockTaskRequest(
        @Schema(
                        description =
                                "Optional. A resolution recorded beside a recurring blocker is what makes the pattern"
                                        + " legible later.")
                @Size(max = 2000)
                String resolution) {}
