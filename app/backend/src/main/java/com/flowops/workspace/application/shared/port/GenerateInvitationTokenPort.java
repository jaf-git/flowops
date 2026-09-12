package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.InvitationToken;

public interface GenerateInvitationTokenPort {
    record MintedToken(InvitationToken token, String hash) {}

    MintedToken mint();

    String hash(String clearToken);
}
