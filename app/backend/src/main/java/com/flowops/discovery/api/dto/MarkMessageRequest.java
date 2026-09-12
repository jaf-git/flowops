package com.flowops.discovery.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record MarkMessageRequest(
        @NotNull UUID messageId,
        @NotNull UUID jobId,
        @NotNull @Pattern(regexp = "REQUEST|COMPLETION|STANDALONE|QUERY") String direction,
        UUID performerId) {}
