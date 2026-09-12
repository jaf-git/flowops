package com.flowops.workspace.application.eraseperson;

import com.flowops.workspace.domain.model.MembershipId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record PreviewErasureResult(
        MembershipId person,
        Optional<String> displayName,
        Optional<Instant> deactivatedAt,
        boolean eligible,
        Optional<String> refusal,
        List<String> destroys,
        List<String> survives,
        boolean alreadyErased) {
    public static final List<String> DESTROYS = List.of("NAME", "EMAIL_ADDRESS", "CREDENTIAL", "SESSIONS");

    public static final List<String> SURVIVES = List.of("AUTHORED_WORK", "REPORTING_HISTORY", "CONSENT_RECORD");
}
