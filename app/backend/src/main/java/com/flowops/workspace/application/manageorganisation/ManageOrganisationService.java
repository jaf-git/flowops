package com.flowops.workspace.application.manageorganisation;

import com.flowops.shared.event.FunctionalRoleCreated;
import com.flowops.shared.event.FunctionalRoleRenamed;
import com.flowops.workspace.application.assignfunctionalrole.UnknownFunctionalRoleException;
import com.flowops.workspace.application.shared.port.OrganisationWritePort;
import com.flowops.workspace.domain.model.FunctionalRole;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageOrganisationService implements ManageOrganisationUseCase {
    private final OrganisationWritePort organisation;

    private final ApplicationEventPublisher announcements;

    public ManageOrganisationService(OrganisationWritePort organisation, ApplicationEventPublisher announcements) {
        this.organisation = organisation;
        this.announcements = announcements;
    }

    @Override
    @Transactional
    public UUID createDepartment(String name) {
        String trimmed = required(name, "a department needs a name");

        organisation.departmentNamed(trimmed).ifPresent(existing -> {
            throw new DepartmentNameTakenException(trimmed);
        });

        UUID id = UUID.randomUUID();
        organisation.saveDepartment(id, trimmed, organisation.nextDepartmentOrder());
        return id;
    }

    @Override
    @Transactional
    public void renameDepartment(UUID departmentId, String name) {
        String trimmed = required(name, "a department needs a name");

        if (!organisation.departmentExists(departmentId)) {
            throw new UnknownDepartmentException(departmentId);
        }

        organisation
                .departmentNamed(trimmed)
                .filter(other -> !other.equals(departmentId))
                .ifPresent(other -> {
                    throw new DepartmentNameTakenException(trimmed);
                });

        organisation.saveDepartment(departmentId, trimmed, organisation.nextDepartmentOrder());
    }

    @Override
    @Transactional
    public void deleteDepartment(UUID departmentId) {
        if (!organisation.departmentExists(departmentId)) {
            throw new UnknownDepartmentException(departmentId);
        }

        long beneath = organisation.rolesIn(departmentId);
        if (beneath > 0) {
            throw new DepartmentHasRolesException(departmentId, beneath);
        }

        organisation.deleteDepartment(departmentId);
    }

    @Override
    @Transactional
    public FunctionalRole createRole(String name, UUID departmentId) {
        String trimmed = required(name, "a role needs a name");

        if (!organisation.departmentExists(departmentId)) {
            throw new UnknownDepartmentException(departmentId);
        }
        organisation.roleNamed(trimmed).ifPresent(existing -> {
            throw new FunctionalRoleNameTakenException(trimmed);
        });

        UUID id = UUID.randomUUID();
        organisation.saveRole(id, trimmed, departmentId, organisation.nextRoleOrder());

        announcements.publishEvent(new FunctionalRoleCreated(id, trimmed));

        return new FunctionalRole(id, trimmed, departmentId);
    }

    @Override
    @Transactional
    public void renameRole(UUID roleId, String name) {
        String trimmed = required(name, "a role needs a name");

        FunctionalRole role =
                organisation.roleById(roleId).orElseThrow(() -> new UnknownFunctionalRoleException(roleId.toString()));

        organisation
                .roleNamed(trimmed)
                .filter(other -> !other.id().equals(roleId))
                .ifPresent(other -> {
                    throw new FunctionalRoleNameTakenException(trimmed);
                });

        organisation.saveRole(role.id(), trimmed, role.departmentId(), organisation.nextRoleOrder());

        announcements.publishEvent(new FunctionalRoleRenamed(roleId, trimmed));
    }

    @Override
    @Transactional
    public void deleteRole(UUID roleId) {
        if (organisation.roleById(roleId).isEmpty()) {
            throw new UnknownFunctionalRoleException(roleId.toString());
        }

        long held = organisation.peopleHolding(roleId);
        if (held > 0) {
            throw new FunctionalRoleIsHeldException(roleId, held);
        }

        organisation.deleteRole(roleId);
    }

    private static String required(String name, String complaint) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(complaint);
        }
        return name.trim();
    }
}
