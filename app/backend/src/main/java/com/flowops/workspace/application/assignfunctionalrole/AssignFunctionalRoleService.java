package com.flowops.workspace.application.assignfunctionalrole;

import com.flowops.shared.event.FunctionalRoleAssigned;
import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.OrganisationChartPort;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignFunctionalRoleService implements AssignFunctionalRoleUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final OrganisationChartPort organisationChartPort;
    private final ApplicationEventPublisher announcements;

    public AssignFunctionalRoleService(
            IdentifyCallerPort identifyCallerPort,
            OrganisationChartPort organisationChartPort,
            ApplicationEventPublisher announcements) {
        this.identifyCallerPort = identifyCallerPort;
        this.organisationChartPort = organisationChartPort;
        this.announcements = announcements;
    }

    @Override
    @Transactional
    public void execute(UUID membershipId, UUID functionalRoleId) {
        identifyCallerPort.currentCaller().orElseThrow(NotAuthenticatedException::new);

        if (functionalRoleId != null) {
            organisationChartPort
                    .roleById(functionalRoleId)
                    .orElseThrow(() -> new UnknownFunctionalRoleException(
                            "there is no functional role with the identifier " + functionalRoleId));
        }

        UUID previousRole = organisationChartPort
                .membership(membershipId)
                .map(OrganisationChartPort.Membership::functionalRoleId)
                .orElse(null);

        organisationChartPort.assign(membershipId, functionalRoleId);

        organisationChartPort
                .membership(membershipId)
                .ifPresent(after -> announcements.publishEvent(
                        new FunctionalRoleAssigned(after.personId(), functionalRoleId, previousRole)));
    }
}
