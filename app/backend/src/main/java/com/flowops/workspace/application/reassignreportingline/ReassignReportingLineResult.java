package com.flowops.workspace.application.reassignreportingline;

import com.flowops.workspace.domain.model.MembershipId;
import java.util.Optional;

public record ReassignReportingLineResult(
        MembershipId membership, Optional<MembershipId> formerManager, MembershipId newManager, boolean changed) {}
