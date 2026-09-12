package com.flowops.discovery.application.digest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DigestReadPort {
    int threadsClosedSince(Instant since);

    Map<UUID, Long> workMillisByType();

    Map<UUID, Long> workMillisByPerformerRoleSince(Instant since);

    List<RoleActivity> roleActivitySince(Instant since);

    record RoleActivity(UUID roleId, String roleName, int peopleHoldingIt, int peopleWhoMarkedWork) {
        public int coveragePercent() {
            return peopleHoldingIt == 0 ? 100 : (peopleWhoMarkedWork * 100) / peopleHoldingIt;
        }
    }
}
