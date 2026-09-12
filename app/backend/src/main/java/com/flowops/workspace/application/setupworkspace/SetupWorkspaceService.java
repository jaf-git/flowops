package com.flowops.workspace.application.setupworkspace;

import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.exception.SetupNotPermittedException;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.LoadWorkspaceSettingsPort;
import com.flowops.workspace.application.shared.port.MarkSetupCompletePort;
import com.flowops.workspace.application.shared.port.SaveMembershipPort;
import com.flowops.workspace.application.shared.port.SaveWorkspacePort;
import com.flowops.workspace.application.shared.port.SaveWorkspaceSettingsPort;
import com.flowops.workspace.application.shared.port.SetDisplayNamePort;
import com.flowops.workspace.domain.enums.MembershipStatus;
import com.flowops.workspace.domain.enums.WorkspaceAction;
import com.flowops.workspace.domain.enums.WorkspaceUse;
import com.flowops.workspace.domain.event.WorkspaceEvent;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.Timezone;
import com.flowops.workspace.domain.model.Workspace;
import com.flowops.workspace.domain.model.WorkspaceName;
import com.flowops.workspace.domain.model.WorkspaceSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SetupWorkspaceService implements SetupWorkspaceUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadWorkspacePort loadWorkspacePort;
    private final SaveWorkspacePort saveWorkspacePort;
    private final LoadWorkspaceSettingsPort loadWorkspaceSettingsPort;
    private final SaveWorkspaceSettingsPort saveWorkspaceSettingsPort;
    private final SetDisplayNamePort setDisplayNamePort;
    private final MarkSetupCompletePort markSetupCompletePort;
    private final SaveMembershipPort saveMembershipPort;
    private final AppendWorkspaceEventPort appendWorkspaceEventPort;
    private final Clock clock;

    public SetupWorkspaceService(
            IdentifyCallerPort identifyCallerPort,
            LoadWorkspacePort loadWorkspacePort,
            SaveWorkspacePort saveWorkspacePort,
            LoadWorkspaceSettingsPort loadWorkspaceSettingsPort,
            SaveWorkspaceSettingsPort saveWorkspaceSettingsPort,
            SetDisplayNamePort setDisplayNamePort,
            MarkSetupCompletePort markSetupCompletePort,
            SaveMembershipPort saveMembershipPort,
            AppendWorkspaceEventPort appendWorkspaceEventPort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadWorkspacePort = loadWorkspacePort;
        this.saveWorkspacePort = saveWorkspacePort;
        this.loadWorkspaceSettingsPort = loadWorkspaceSettingsPort;
        this.saveWorkspaceSettingsPort = saveWorkspaceSettingsPort;
        this.setDisplayNamePort = setDisplayNamePort;
        this.markSetupCompletePort = markSetupCompletePort;
        this.saveMembershipPort = saveMembershipPort;
        this.appendWorkspaceEventPort = appendWorkspaceEventPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SetupWorkspaceResult execute(SetupWorkspaceCommand command) {
        IdentifyCallerPort.Caller caller =
                identifyCallerPort.currentCaller().orElseThrow(NotAuthenticatedException::new);
        if (!caller.ownsWorkspaceSetup()) {
            throw new SetupNotPermittedException();
        }

        Workspace workspace = loadWorkspacePort.load();
        if (caller.setupCompleted()) {
            return asItStands(workspace, caller.landingTarget());
        }

        WorkspaceName name = new WorkspaceName(command.workspaceName());
        Timezone timezone = new Timezone(command.timezone());
        Instant now = clock.instant();

        setDisplayNamePort.setCallerDisplayName(command.ownerName());
        saveWorkspacePort.save(workspace.named(name, command.use()));
        saveWorkspaceSettingsPort.save(WorkspaceSettings.inForceFrom(workspace.id(), timezone, now));

        saveMembershipPort.save(
                workspace.id(),
                new Membership(MembershipId.of(UUID.randomUUID()), caller.id(), MembershipStatus.ACTIVE, null));

        String landingTarget = markSetupCompletePort.markCallerSetupComplete();
        appendWorkspaceEventPort.append(
                WorkspaceEvent.byActor(WorkspaceAction.SETUP_COMPLETED, caller.id(), workspace.id(), now));

        return new SetupWorkspaceResult(workspace.id(), name, command.use(), timezone, landingTarget);
    }

    private SetupWorkspaceResult asItStands(Workspace workspace, String landingTarget) {
        if (!workspace.isNamed()) {
            throw new IllegalStateException("setup is complete but the workspace was never named");
        }
        Timezone timezone = loadWorkspaceSettingsPort
                .inForce(workspace.id())
                .map(WorkspaceSettings::timezone)
                .orElseThrow(() -> new IllegalStateException("setup is complete but no settings row is in force"));
        WorkspaceName name = workspace
                .name()
                .orElseThrow(() -> new IllegalStateException("setup is complete but the workspace has no name"));
        WorkspaceUse use = workspace
                .use()
                .orElseThrow(() -> new IllegalStateException("setup is complete but the workspace has no use"));

        return new SetupWorkspaceResult(workspace.id(), name, use, timezone, landingTarget);
    }
}
