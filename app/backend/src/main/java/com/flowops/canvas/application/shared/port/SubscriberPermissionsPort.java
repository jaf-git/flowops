package com.flowops.canvas.application.shared.port;

import java.util.Set;
import java.util.UUID;

public interface SubscriberPermissionsPort {
    Set<String> heldBy(UUID person);
}
