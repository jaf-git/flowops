package com.flowops.process.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TemplateResponse(
        UUID id,
        String name,
        String overview,
        boolean active,
        UUID authorId,
        Instant createdAt,
        List<StepResponse> steps,
        List<DependencyResponse> dependencies,
        ProcessMetadataResponse metadata) {}
