package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.Department;
import com.flowops.workspace.domain.model.FunctionalRole;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface OrganisationChartPort {
    List<Department> chart();

    Optional<FunctionalRole> roleById(UUID functionalRoleId);

    void assign(UUID membershipId, UUID functionalRoleId);

    Optional<Membership> membership(UUID membershipId);

    Map<UUID, UUID> assignments();

    record Membership(UUID personId, UUID functionalRoleId) {}
}
