package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OwnDataResponse(
        @Schema(description = "The account, including the address, which is the caller's own.") AccountResponse account,
        @Schema(description = "Their place here.") MembershipResponse membership,
        @Schema(description = "Who they reported to, and between when. Oldest first.")
                List<ReportingPeriodResponse> reportingLineHistory,
        @Schema(description = "What they agreed to, as they agreed to it. Absent for an account that never did.")
                ConsentResponse consent,
        @Schema(description = "A summary of what they authored -- counts and never content.") AuthoredResponse authored,
        @Schema(description = "How many copies have been produced recently, and the ceiling.") ExportsResponse exports,
        @Schema(description = "True when the owner produced this on a deactivated person's behalf.")
                boolean producedForSomebodyElse) {
    public record AccountResponse(
            @Schema(description = "The person's identifier, which is what their work resolves through.") UUID personId,
            String emailAddress,
            @Schema(nullable = true) String displayName,
            String role,
            String accountState,
            Instant createdAt,
            List<SessionResponse> sessions) {}

    public record SessionResponse(
            UUID reference,
            @Schema(nullable = true) String deviceSummary,
            @Schema(nullable = true) String coarseLocation,
            Instant createdAt) {}

    public record MembershipResponse(
            UUID membershipId,
            String status,
            @Schema(nullable = true) Instant deactivatedAt,
            @Schema(nullable = true) String managerName) {}

    public record ReportingPeriodResponse(
            @Schema(nullable = true) String managerName, Instant from, @Schema(nullable = true) Instant until) {}

    public record ConsentResponse(String version, String language, Instant agreedAt) {}

    public record AuthoredResponse(int tasks, int comments, int approvals) {}

    public record ExportsResponse(int produced, int limit) {}
}
