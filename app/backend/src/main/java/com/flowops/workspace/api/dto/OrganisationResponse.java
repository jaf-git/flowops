package com.flowops.workspace.api.dto;

import com.flowops.workspace.domain.model.Department;
import java.util.List;
import java.util.UUID;

public record OrganisationResponse(List<DepartmentResponse> departments) {
    public static OrganisationResponse of(List<Department> departments) {
        return new OrganisationResponse(
                departments.stream().map(DepartmentResponse::of).toList());
    }

    public record DepartmentResponse(UUID id, String name, List<FunctionalRoleResponse> roles) {
        static DepartmentResponse of(Department department) {
            return new DepartmentResponse(
                    department.id(),
                    department.name(),
                    department.roles().stream()
                            .map(role -> new FunctionalRoleResponse(role.id(), role.name()))
                            .toList());
        }
    }

    public record FunctionalRoleResponse(UUID id, String name) {}
}
