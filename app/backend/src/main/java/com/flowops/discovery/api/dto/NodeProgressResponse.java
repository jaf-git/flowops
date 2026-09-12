package com.flowops.discovery.api.dto;

import java.util.UUID;

public record NodeProgressResponse(
        UUID nodeId,
        String state,
        String outputType,
        String openPhase,
        String waitingOn,
        UUID pairedWith,
        boolean asksForAnOutput) {}
