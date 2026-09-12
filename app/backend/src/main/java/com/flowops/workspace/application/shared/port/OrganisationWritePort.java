package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.FunctionalRole;
import java.util.Optional;
import java.util.UUID;

public interface OrganisationWritePort {
    boolean departmentExists(UUID departmentId);

    Optional<UUID> departmentNamed(String name);

    void saveDepartment(UUID id, String name, int displayOrder);

    void deleteDepartment(UUID departmentId);

    long rolesIn(UUID departmentId);

    int nextDepartmentOrder();

    Optional<FunctionalRole> roleById(UUID roleId);

    Optional<FunctionalRole> roleNamed(String name);

    void saveRole(UUID id, String name, UUID departmentId, int displayOrder);

    void deleteRole(UUID roleId);

    int nextRoleOrder();

    long peopleHolding(UUID roleId);
}
