package com.flowops.workspace.application.manageorganisation;

import com.flowops.workspace.domain.model.FunctionalRole;
import java.util.UUID;

public interface ManageOrganisationUseCase {
    UUID createDepartment(String name);

    void renameDepartment(UUID departmentId, String name);

    void deleteDepartment(UUID departmentId);

    FunctionalRole createRole(String name, UUID departmentId);

    void renameRole(UUID roleId, String name);

    void deleteRole(UUID roleId);
}
