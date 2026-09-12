package com.flowops.discovery.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;

public record EnrichNodeRequest(
        @Schema(description = "What people call this work, at most 120 characters", example = "caption set for Aurora")
                @Size(max = 120, message = "a title is a name to scan a list by, not a sentence")
                String title,
        @Schema(description = "The longer description of what the work involves") String detail,
        @Schema(description = "The steps. An empty list means deliberately none; omit the field to leave it unanswered")
                List<@Size(max = 240) String> checklist) {}
