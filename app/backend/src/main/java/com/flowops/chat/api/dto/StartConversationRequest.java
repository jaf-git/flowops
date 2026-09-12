package com.flowops.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartConversationRequest(
        @Schema(description = "The person to start a conversation with") @NotNull UUID personId) {}
