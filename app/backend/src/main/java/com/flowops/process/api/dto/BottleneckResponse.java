package com.flowops.process.api.dto;

import java.util.UUID;

public record BottleneckResponse(UUID stepId, long waitedMinutes) {}
