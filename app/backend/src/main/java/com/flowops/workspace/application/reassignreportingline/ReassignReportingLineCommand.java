package com.flowops.workspace.application.reassignreportingline;

import com.flowops.workspace.domain.model.MembershipId;

public record ReassignReportingLineCommand(MembershipId person, MembershipId proposedManager) {}
