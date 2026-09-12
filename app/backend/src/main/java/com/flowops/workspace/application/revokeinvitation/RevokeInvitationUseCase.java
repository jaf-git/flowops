package com.flowops.workspace.application.revokeinvitation;

public interface RevokeInvitationUseCase {
    RevokeInvitationResult execute(RevokeInvitationCommand command);
}
