package com.flowops.process.api.dto;

import java.util.UUID;

public record InstanceEdgeResponse(UUID from, UUID to, boolean satisfied) {}
