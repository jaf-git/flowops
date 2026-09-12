package com.flowops.workspace.application.acceptinvite;

import com.flowops.workspace.application.shared.exception.InvitationNotUsableException;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import com.flowops.workspace.application.shared.port.GenerateInvitationTokenPort;
import com.flowops.workspace.application.shared.port.LoadInvitationPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.SaveInvitationPort;
import com.flowops.workspace.domain.enums.WorkspaceAction;
import com.flowops.workspace.domain.event.WorkspaceEvent;
import com.flowops.workspace.domain.model.Invitation;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeclineInvitationService implements DeclineInvitationUseCase {
    private final LoadInvitationPort loadInvitationPort;
    private final SaveInvitationPort saveInvitationPort;
    private final LoadWorkspacePort loadWorkspacePort;
    private final GenerateInvitationTokenPort generateInvitationTokenPort;
    private final AppendWorkspaceEventPort appendWorkspaceEventPort;
    private final Clock clock;

    public DeclineInvitationService(
            LoadInvitationPort loadInvitationPort,
            SaveInvitationPort saveInvitationPort,
            LoadWorkspacePort loadWorkspacePort,
            GenerateInvitationTokenPort generateInvitationTokenPort,
            AppendWorkspaceEventPort appendWorkspaceEventPort,
            Clock clock) {
        this.loadInvitationPort = loadInvitationPort;
        this.saveInvitationPort = saveInvitationPort;
        this.loadWorkspacePort = loadWorkspacePort;
        this.generateInvitationTokenPort = generateInvitationTokenPort;
        this.appendWorkspaceEventPort = appendWorkspaceEventPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void execute(String token) {
        Instant now = clock.instant();
        Invitation invitation = loadInvitationPort
                .findByTokenHashForUpdate(generateInvitationTokenPort.hash(token))
                .orElseThrow(InvitationNotUsableException::new);
        if (!invitation.isUsableAt(now)) {
            throw new InvitationNotUsableException();
        }

        saveInvitationPort.save(invitation.declineAt(now));
        appendWorkspaceEventPort.append(WorkspaceEvent.byActor(
                WorkspaceAction.INVITATION_DECLINED,
                null,
                loadWorkspacePort.load().id(),
                now));
    }
}
