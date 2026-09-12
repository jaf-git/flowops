package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.event.WorkspaceEvent;
import com.flowops.workspace.domain.model.PersonId;
import java.util.List;

public interface LoadWorkspaceEventsPort {
    List<WorkspaceEvent> reportingLineChangesFor(PersonId person);
}
