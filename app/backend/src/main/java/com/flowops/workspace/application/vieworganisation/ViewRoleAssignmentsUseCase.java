package com.flowops.workspace.application.vieworganisation;

import java.util.Map;
import java.util.UUID;

public interface ViewRoleAssignmentsUseCase {
    Map<UUID, UUID> execute();
}
