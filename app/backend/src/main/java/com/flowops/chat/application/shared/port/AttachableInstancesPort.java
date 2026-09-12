package com.flowops.chat.application.shared.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachableInstancesPort {
    List<Attachable> forSomebodyToChoose(Optional<UUID> preferring);

    record Attachable(UUID id, String name) {}
}
