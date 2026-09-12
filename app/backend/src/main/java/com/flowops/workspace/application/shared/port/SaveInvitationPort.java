package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.Invitation;

public interface SaveInvitationPort {
    Invitation save(Invitation invitation);
}
