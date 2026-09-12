package com.flowops.discovery.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignOrphanRequest(@NotNull UUID trackId) {}
