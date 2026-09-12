package com.flowops.process.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StartFromDescriptionsRequest(
        @NotBlank @Size(max = 200) @Schema(example = "Aurora Coffee — brand strategy") String name,
        @NotNull @Schema(description = "Who steers the run. Must be active.") UUID processOwnerId,
        @NotEmpty @Size(max = 50) @Valid List<@NotNull Step> steps) {
    public record Step(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description,
            @NotNull UUID assigneeId,
            Instant deadline,
            @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT") String priority) {}
}
