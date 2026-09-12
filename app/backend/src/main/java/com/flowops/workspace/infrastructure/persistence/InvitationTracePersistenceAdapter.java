package com.flowops.workspace.infrastructure.persistence;

import com.flowops.workspace.application.shared.port.EraseInvitationTracesPort;
import com.flowops.workspace.infrastructure.persistence.repository.WorkspaceInvitationJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class InvitationTracePersistenceAdapter implements EraseInvitationTracesPort {
    private final WorkspaceInvitationJpaRepository invitations;

    public InvitationTracePersistenceAdapter(WorkspaceInvitationJpaRepository invitations) {
        this.invitations = invitations;
    }

    @Override
    public void replaceAddress(String realAddress, String opaqueAddress) {
        invitations.replaceAddress(realAddress, opaqueAddress);
    }
}
