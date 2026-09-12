package com.flowops.workspace.infrastructure.persistence;

import com.flowops.workspace.application.shared.port.OrganisationWritePort;
import com.flowops.workspace.domain.model.FunctionalRole;
import com.flowops.workspace.infrastructure.persistence.entity.DepartmentJpaEntity;
import com.flowops.workspace.infrastructure.persistence.entity.FunctionalRoleJpaEntity;
import com.flowops.workspace.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.flowops.workspace.infrastructure.persistence.repository.FunctionalRoleJpaRepository;
import com.flowops.workspace.infrastructure.persistence.repository.WorkspaceMembershipJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrganisationWritePersistenceAdapter implements OrganisationWritePort {
    private final DepartmentJpaRepository departments;
    private final FunctionalRoleJpaRepository roles;
    private final WorkspaceMembershipJpaRepository memberships;
    private final Clock clock;

    public OrganisationWritePersistenceAdapter(
            DepartmentJpaRepository departments,
            FunctionalRoleJpaRepository roles,
            WorkspaceMembershipJpaRepository memberships,
            Clock clock) {
        this.departments = departments;
        this.roles = roles;
        this.memberships = memberships;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean departmentExists(UUID departmentId) {
        return departments.existsById(departmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> departmentNamed(String name) {
        return departments.findAll().stream()
                .filter(department -> department.getName().equalsIgnoreCase(name))
                .map(DepartmentJpaEntity::getId)
                .findFirst();
    }

    @Override
    @Transactional
    public void saveDepartment(UUID id, String name, int displayOrder) {
        departments.save(new DepartmentJpaEntity(id, name, displayOrder, Instant.now(clock)));
    }

    @Override
    @Transactional
    public void deleteDepartment(UUID departmentId) {
        departments.deleteById(departmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public long rolesIn(UUID departmentId) {
        return roles.findAll().stream()
                .filter(role -> role.getDepartmentId().equals(departmentId))
                .count();
    }

    @Override
    @Transactional(readOnly = true)
    public int nextDepartmentOrder() {
        return departments.findAll().stream()
                        .mapToInt(DepartmentJpaEntity::getDisplayOrder)
                        .max()
                        .orElse(0)
                + 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FunctionalRole> roleById(UUID roleId) {
        return roles.findById(roleId).map(OrganisationWritePersistenceAdapter::role);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FunctionalRole> roleNamed(String name) {
        return roles.findByNameIgnoringCase(name).map(OrganisationWritePersistenceAdapter::role);
    }

    @Override
    @Transactional
    public void saveRole(UUID id, String name, UUID departmentId, int displayOrder) {
        Instant createdAt =
                roles.findById(id).map(FunctionalRoleJpaEntity::getCreatedAt).orElseGet(() -> Instant.now(clock));

        roles.save(new FunctionalRoleJpaEntity(id, name, departmentId, displayOrder, createdAt));
    }

    @Override
    @Transactional
    public void deleteRole(UUID roleId) {
        roles.deleteById(roleId);
    }

    @Override
    @Transactional(readOnly = true)
    public int nextRoleOrder() {
        return roles.findAll().stream()
                        .mapToInt(FunctionalRoleJpaEntity::getDisplayOrder)
                        .max()
                        .orElse(0)
                + 1;
    }

    @Override
    @Transactional(readOnly = true)
    public long peopleHolding(UUID roleId) {
        return memberships.countByFunctionalRoleId(roleId);
    }

    private static FunctionalRole role(FunctionalRoleJpaEntity row) {
        return new FunctionalRole(row.getId(), row.getName(), row.getDepartmentId());
    }
}
