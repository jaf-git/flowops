package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.MembershipId;

public interface ReassignManagerPort {
    void reassign(MembershipId membership, MembershipId newManager);
}
