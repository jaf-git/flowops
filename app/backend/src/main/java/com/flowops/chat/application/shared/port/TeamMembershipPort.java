package com.flowops.chat.application.shared.port;

import java.util.Set;
import java.util.UUID;

public interface TeamMembershipPort {
    Set<UUID> directReportsOf(UUID managerUserId);

    Set<UUID> teamOf(UUID managerUserId);
}
