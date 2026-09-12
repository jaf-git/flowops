package com.flowops.process.api.dto;

import java.util.UUID;

public record TemplateSummaryResponse(UUID id, String name, String overview, int stepCount, boolean active) {}
