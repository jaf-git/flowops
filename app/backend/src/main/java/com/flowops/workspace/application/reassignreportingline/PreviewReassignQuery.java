package com.flowops.workspace.application.reassignreportingline;

import com.flowops.workspace.domain.model.MembershipId;

public record PreviewReassignQuery(MembershipId person, MembershipId proposedManager) {}
