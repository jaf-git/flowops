package com.flowops.workspace.application.editownprofile;

import com.flowops.workspace.application.shared.exception.MemberNameRequiredException;
import com.flowops.workspace.application.shared.exception.MembershipNotFoundException;
import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.SetDisplayNamePort;
import com.flowops.workspace.domain.event.WorkspaceEvent;
import com.flowops.workspace.domain.model.PersonId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EditOwnProfileService implements EditOwnProfileUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final DescribePeoplePort describePeoplePort;
    private final SetDisplayNamePort setDisplayNamePort;
    private final AppendWorkspaceEventPort appendWorkspaceEventPort;
    private final LoadWorkspacePort loadWorkspacePort;
    private final Clock clock;

    public EditOwnProfileService(
            IdentifyCallerPort identifyCallerPort,
            DescribePeoplePort describePeoplePort,
            SetDisplayNamePort setDisplayNamePort,
            AppendWorkspaceEventPort appendWorkspaceEventPort,
            LoadWorkspacePort loadWorkspacePort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.describePeoplePort = describePeoplePort;
        this.setDisplayNamePort = setDisplayNamePort;
        this.appendWorkspaceEventPort = appendWorkspaceEventPort;
        this.loadWorkspacePort = loadWorkspacePort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EditOwnProfileResult execute(EditOwnProfileCommand command) {
        Instant now = clock.instant();
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(NotAuthenticatedException::new)
                .id();

        String submitted =
                command.displayName() == null ? "" : command.displayName().trim();
        if (submitted.isEmpty()) {
            throw new MemberNameRequiredException();
        }

        String held = currentNameOf(caller).orElseThrow(MembershipNotFoundException::new);

        if (submitted.equals(held)) {
            return new EditOwnProfileResult(held, false);
        }

        setDisplayNamePort.setCallerDisplayName(submitted);
        appendWorkspaceEventPort.append(
                WorkspaceEvent.profileChanged(caller, loadWorkspacePort.load().id(), now));

        return new EditOwnProfileResult(submitted, true);
    }

    private Optional<String> currentNameOf(PersonId caller) {
        return describePeoplePort.describe(List.of(caller)).stream()
                .findFirst()
                .map(DescribePeoplePort.PersonDescription::displayName);
    }
}
