package com.flowops.workspace.application.assignfunctionalrole;

import java.util.UUID;

public interface AssignFunctionalRoleUseCase {
    void execute(UUID membershipId, UUID functionalRoleId);
}
