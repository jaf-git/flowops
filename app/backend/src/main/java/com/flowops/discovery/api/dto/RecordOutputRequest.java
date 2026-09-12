package com.flowops.discovery.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RecordOutputRequest(@NotNull @Pattern(regexp = "TEXT|DESIGN|REPORT|SCHEDULING|NONE") String outputType) {}
