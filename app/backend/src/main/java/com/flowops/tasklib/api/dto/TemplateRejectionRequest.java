package com.flowops.tasklib.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TemplateRejectionRequest(
        @NotBlank(message = "a template sent back needs a reason its author can act on") @Size(max = 1000)
                String reason) {}
