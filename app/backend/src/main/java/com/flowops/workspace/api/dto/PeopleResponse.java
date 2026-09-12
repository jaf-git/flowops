package com.flowops.workspace.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PeopleResponse(
        List<PersonResponse> people, List<PendingInvitationResponse> invitations, boolean onlyMember) {
    public record PersonResponse(
            @Schema(example = "3f1a6c58-8f7a-4a1e-9f2b-0c5d4e3a2b19") UUID membershipId,
            @Schema(example = "b2c3d4e5-6f70-4812-9a3b-4c5d6e7f8091") UUID personId,
            @Schema(example = "Maria Ionescu") String displayName,
            @Schema(example = "OWNER") String role,
            @Schema(example = "null", nullable = true) UUID managerId,
            @Schema(example = "ACTIVE") String status,
            @Schema(example = "2026-07-02T09:00:00Z", nullable = true) Instant deactivatedAt,
            @Schema(example = "true") boolean isSelf) {}

    public record PendingInvitationResponse(
            @Schema(example = "0d0a4c9a-2b7d-4a1e-8c3f-1b2a3c4d5e6f") UUID id,
            @Schema(example = "stefan@atelier.ro") String emailAddress,
            @Schema(example = "EMPLOYEE") String intendedRole,
            @Schema(example = "3f1a6c58-8f7a-4a1e-9f2b-0c5d4e3a2b19") UUID intendedManagerId,
            @Schema(example = "3f1a6c58-8f7a-4a1e-9f2b-0c5d4e3a2b19") UUID inviterId,
            @Schema(example = "SENT") String state,
            @Schema(example = "2026-08-11T09:00:00Z") Instant expiresAt) {}
}
