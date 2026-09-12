package com.flowops.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteTaskRequest(
        @Schema(description = "What was done. The reviewer judges this rather than taking the assignee's word.")
                @NotBlank
                @Size(max = 4000)
                String note,
        @Schema(description = "Optional. A string the reviewer may follow; the system never retrieves it.")
                @Size(max = 2000)
                String externalLink) {}
