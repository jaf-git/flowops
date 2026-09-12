package com.flowops.process.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "The people a step of this run may be assigned to.")
public record AssignablePeopleResponse(List<Candidate> people) {
    @Schema(description = "Somebody a step may be assigned to.")
    public record Candidate(UUID id, String displayName) {}
}
