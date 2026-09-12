package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.MembershipId;

public interface InvitationLimitPort {
    void check(MembershipId inviter, EmailAddress invited);
}
