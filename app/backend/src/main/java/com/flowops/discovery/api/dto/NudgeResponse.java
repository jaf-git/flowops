package com.flowops.discovery.api.dto;

import java.util.UUID;

public record NudgeResponse(UUID nodeId, UUID jobId, UUID trackId, String text, String state) {}
