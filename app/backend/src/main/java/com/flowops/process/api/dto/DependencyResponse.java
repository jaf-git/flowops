package com.flowops.process.api.dto;

import java.util.UUID;

public record DependencyResponse(UUID dependentStepId, UUID dependsOnStepId) {}
