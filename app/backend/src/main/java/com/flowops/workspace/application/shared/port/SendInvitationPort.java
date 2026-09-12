package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.InvitationToken;

public interface SendInvitationPort {
    void sendInvitation(EmailAddress to, InvitationToken token);
}
