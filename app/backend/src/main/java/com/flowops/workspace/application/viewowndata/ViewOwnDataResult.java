package com.flowops.workspace.application.viewowndata;

import com.flowops.workspace.application.shared.port.DescribeAccountPort;
import com.flowops.workspace.domain.enums.MembershipStatus;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record ViewOwnDataResult(
        DescribeAccountPort.Account account,
        MembershipId membership,
        MembershipStatus status,
        Optional<Instant> deactivatedAt,
        Optional<String> managerName,
        List<ReportingLinePeriod> reportingLineHistory,
        Optional<Consent> consent,
        Authored authored,
        int exportsProduced,
        int exportLimit,
        boolean producedForSomebodyElse) {
    public record ReportingLinePeriod(Optional<String> managerName, Instant from, Optional<Instant> until) {}

    public record Consent(String version, String language, Instant agreedAt) {}

    public record Authored(int tasks, int comments, int approvals) {
        public static Authored nothingYet() {
            return new Authored(0, 0, 0);
        }
    }

    public PersonId person() {
        return account.person();
    }
}
