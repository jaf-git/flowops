package com.flowops.process.api.dto;

import java.util.UUID;

public record StepResponse(
        UUID id,
        UUID taskTemplateId,
        String title,
        String description,
        Integer expectedDurationHours,
        int position,
        boolean optional,
        String conditionNote) {}
