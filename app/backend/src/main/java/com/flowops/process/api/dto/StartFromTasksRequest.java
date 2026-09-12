package com.flowops.process.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record StartFromTasksRequest(
        @NotBlank @Size(max = 200) @Schema(example = "Închidere lunară august") String name,
        @NotNull @Schema(description = "The person who steers this run. May be an employee") UUID processOwnerId,
        @NotEmpty
                @Size(max = 50)
                @Schema(
                        description = "Existing tasks, in reading order. Each must be visible to the caller "
                                + "and belong to no run.")
                List<@NotNull UUID> taskIds) {}
