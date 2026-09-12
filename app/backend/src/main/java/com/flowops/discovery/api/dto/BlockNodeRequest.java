package com.flowops.discovery.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record BlockNodeRequest(@NotNull @Pattern(regexp = "CLIENT|SUPPLIER|COLLEAGUE|APPROVAL") String waitingOn) {}
