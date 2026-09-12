package com.flowops.task.api.dto;

import com.flowops.task.domain.enums.LinkRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AttachLinkRequest(
        @Schema(example = "https://drive.example.com/q3-review.xlsx", description = "http or https only")
                @NotNull
                @Size(max = 2000)
                String url,
        @Schema(description = "What to call it. The host is shown when this is empty.") @Size(max = 200) String label,
        @Schema(description = "What it is to the work: what it is done from, what it produced, or context") @NotNull
                LinkRole role) {}
