package com.flowops.workspace.application.vieworganisation;

import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.OrganisationChartPort;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewRoleAssignmentsService implements ViewRoleAssignmentsUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final OrganisationChartPort organisationChartPort;

    public ViewRoleAssignmentsService(
            IdentifyCallerPort identifyCallerPort, OrganisationChartPort organisationChartPort) {
        this.identifyCallerPort = identifyCallerPort;
        this.organisationChartPort = organisationChartPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UUID> execute() {
        identifyCallerPort.currentCaller().orElseThrow(NotAuthenticatedException::new);
        return organisationChartPort.assignments();
    }
}
