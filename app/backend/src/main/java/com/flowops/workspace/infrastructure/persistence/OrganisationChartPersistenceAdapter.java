package com.flowops.workspace.infrastructure.persistence;

import com.flowops.workspace.application.shared.port.OrganisationChartPort;
import com.flowops.workspace.domain.model.Department;
import com.flowops.workspace.domain.model.FunctionalRole;
import com.flowops.workspace.infrastructure.persistence.entity.FunctionalRoleJpaEntity;
import com.flowops.workspace.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.flowops.workspace.infrastructure.persistence.repository.FunctionalRoleJpaRepository;
import com.flowops.workspace.infrastructure.persistence.repository.WorkspaceMembershipJpaRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrganisationChartPersistenceAdapter implements OrganisationChartPort {
    private final DepartmentJpaRepository departments;
    private final FunctionalRoleJpaRepository roles;
    private final WorkspaceMembershipJpaRepository memberships;

    public OrganisationChartPersistenceAdapter(
            DepartmentJpaRepository departments,
            FunctionalRoleJpaRepository roles,
            WorkspaceMembershipJpaRepository memberships) {
        this.departments = departments;
        this.roles = roles;
        this.memberships = memberships;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Department> chart() {
        Map<UUID, List<FunctionalRoleJpaEntity>> byDepartment = roles.findAllByOrderByDisplayOrderAsc().stream()
                .collect(Collectors.groupingBy(FunctionalRoleJpaEntity::getDepartmentId));

        return departments.findAllByOrderByDisplayOrderAsc().stream()
                .map(department -> new Department(
                        department.getId(),
                        department.getName(),
                        byDepartment.getOrDefault(department.getId(), List.of()).stream()
                                .map(OrganisationChartPersistenceAdapter::asRole)
                                .toList()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FunctionalRole> roleById(UUID functionalRoleId) {
        return roles.findById(functionalRoleId).map(OrganisationChartPersistenceAdapter::asRole);
    }

    @Override
    @Transactional
    public void assign(UUID membershipId, UUID functionalRoleId) {
        memberships.assignFunctionalRole(membershipId, functionalRoleId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Membership> membership(UUID membershipId) {
        return memberships
                .findById(membershipId)
                .map(row -> new Membership(row.getUserId(), row.getFunctionalRoleId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UUID> assignments() {
        Map<UUID, UUID> held = new LinkedHashMap<>();
        memberships.findAll().stream()
                .filter(row -> row.getFunctionalRoleId() != null)
                .forEach(row -> held.put(row.getId(), row.getFunctionalRoleId()));
        return held;
    }

    private static FunctionalRole asRole(FunctionalRoleJpaEntity entity) {
        return new FunctionalRole(entity.getId(), entity.getName(), entity.getDepartmentId());
    }
}
