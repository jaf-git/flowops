package com.flowops.nodepipeline.api.dto;

import com.flowops.nodepipeline.application.resemble.ResembleApprovedWorkUseCase;
import io.swagger.v3.oas.annotations.media.Schema;

public record ResemblanceResponse(Match match) {
    public static ResemblanceResponse of(ResembleApprovedWorkUseCase.Resemblance found) {
        return new ResemblanceResponse(
                found == null ? null : new Match(found.templateId(), found.title(), found.score(), found.why()));
    }

    public record Match(
            @Schema(example = "37c060c0-0f15-4c16-97c5-c44cdeb64516") String templateId,
            @Schema(example = "Monthly write-up — October Iulius") String title,
            @Schema(example = "0.83") double score,
            @Schema(example = "matched_ad_hoc") String why) {}
}
