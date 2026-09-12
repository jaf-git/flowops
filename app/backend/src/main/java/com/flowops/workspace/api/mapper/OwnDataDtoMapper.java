package com.flowops.workspace.api.mapper;

import com.flowops.workspace.api.dto.OwnDataResponse;
import com.flowops.workspace.application.shared.port.DescribeAccountPort;
import com.flowops.workspace.application.viewowndata.ViewOwnDataResult;
import org.springframework.stereotype.Component;

@Component
public class OwnDataDtoMapper {
    public OwnDataResponse toResponse(ViewOwnDataResult result) {
        return new OwnDataResponse(
                account(result.account()),
                new OwnDataResponse.MembershipResponse(
                        result.membership().value(),
                        result.status().name(),
                        result.deactivatedAt().orElse(null),
                        result.managerName().orElse(null)),
                result.reportingLineHistory().stream()
                        .map(period -> new OwnDataResponse.ReportingPeriodResponse(
                                period.managerName().orElse(null),
                                period.from(),
                                period.until().orElse(null)))
                        .toList(),
                result.consent()
                        .map(consent -> new OwnDataResponse.ConsentResponse(
                                consent.version(), consent.language(), consent.agreedAt()))
                        .orElse(null),
                new OwnDataResponse.AuthoredResponse(
                        result.authored().tasks(),
                        result.authored().comments(),
                        result.authored().approvals()),
                new OwnDataResponse.ExportsResponse(result.exportsProduced(), result.exportLimit()),
                result.producedForSomebodyElse());
    }

    private OwnDataResponse.AccountResponse account(DescribeAccountPort.Account account) {
        return new OwnDataResponse.AccountResponse(
                account.person().value(),
                account.emailAddress(),
                account.displayName().orElse(null),
                account.role(),
                account.accountState(),
                account.createdAt(),
                account.sessions().stream()
                        .map(session -> new OwnDataResponse.SessionResponse(
                                session.reference(),
                                session.deviceSummary().orElse(null),
                                session.coarseLocation().orElse(null),
                                session.createdAt()))
                        .toList());
    }
}
