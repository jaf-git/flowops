package com.flowops.tasklib.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record StampTaskRequest(
        @Size(max = 200) String title,
        @Size(max = 4000) String description,
        @NotNull(message = "work is given to somebody") UUID assigneeId,
        Instant deadline,
        @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT") String priority) {}
