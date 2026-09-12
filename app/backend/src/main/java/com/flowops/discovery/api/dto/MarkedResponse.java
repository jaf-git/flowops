package com.flowops.discovery.api.dto;

import java.util.UUID;

public record MarkedResponse(UUID nodeId, UUID jobId, UUID trackId, boolean weaklyKeyed) {}
