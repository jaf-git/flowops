package com.flowops.workspace.application.vieworganisation;

import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.OrganisationChartPort;
import com.flowops.workspace.domain.model.Department;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewOrganisationService implements ViewOrganisationUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final OrganisationChartPort organisationChartPort;

    public ViewOrganisationService(IdentifyCallerPort identifyCallerPort, OrganisationChartPort organisationChartPort) {
        this.identifyCallerPort = identifyCallerPort;
        this.organisationChartPort = organisationChartPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Department> execute() {
        identifyCallerPort.currentCaller().orElseThrow(NotAuthenticatedException::new);
        return organisationChartPort.chart();
    }
}
