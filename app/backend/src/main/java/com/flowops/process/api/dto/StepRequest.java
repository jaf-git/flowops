package com.flowops.process.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record StepRequest(
        @Schema(description = "Absent when adding a step; the existing identifier when keeping one") UUID id,
        @Schema(description = "The task template this step is the work of. Required.") UUID taskTemplateId,
        @Schema(description = "Optional. How long this process expects to wait on this step; a process runs without it")
                Integer expectedDurationHours,
        @Schema(
                        description = "Whether this step always applies. When true, the run owner is asked once, "
                                + "as the step becomes reachable, and may skip it (SOP_01 section 5)")
                boolean optional,
        @Schema(description = "When it applies, in words — \"is the value above 5,000?\". Requires optional.")
                String conditionNote) {}
