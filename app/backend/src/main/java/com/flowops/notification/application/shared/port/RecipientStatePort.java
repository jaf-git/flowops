package com.flowops.notification.application.shared.port;

import java.util.Set;
import java.util.UUID;

public interface RecipientStatePort {
    Set<UUID> stillHere(Set<UUID> userIds);
}
