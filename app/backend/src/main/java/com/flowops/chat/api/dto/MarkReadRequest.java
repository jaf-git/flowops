package com.flowops.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MarkReadRequest(
        @Schema(description = "The newest message the caller has seen") @NotNull UUID throughMessageId) {}
