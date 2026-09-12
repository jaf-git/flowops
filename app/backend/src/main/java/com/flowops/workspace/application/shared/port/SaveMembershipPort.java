package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.WorkspaceId;
import java.time.Instant;

public interface SaveMembershipPort {
    Membership save(WorkspaceId workspace, Membership membership);

    void deactivate(MembershipId membership, Instant at);

    void erase(MembershipId membership, Instant at);
}
