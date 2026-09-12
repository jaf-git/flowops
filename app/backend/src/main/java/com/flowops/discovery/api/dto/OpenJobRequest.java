package com.flowops.discovery.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record OpenJobRequest(
        @NotNull UUID messageId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 120) String projectLabel,
        UUID counterpartyId,
        UUID reworkOfJobId,
        boolean evenThoughOneIsOpen) {}
