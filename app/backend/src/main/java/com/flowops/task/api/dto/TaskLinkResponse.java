package com.flowops.task.api.dto;

import com.flowops.task.domain.enums.LinkRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record TaskLinkResponse(
        UUID id,
        @Schema(description = "http or https, judged when it was attached") String url,
        String label,
        String displayText,
        LinkRole role,
        UUID addedById,
        Instant addedAt) {}
