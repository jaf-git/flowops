package com.flowops.process.application.addtask;

import com.flowops.process.domain.model.InstanceId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ViewAttachableInstancesUseCase {
    List<Attachable> forSomebodyToChoose(Optional<UUID> preferring);

    record Attachable(UUID id, String name) {
        public Attachable(InstanceId id, String name) {
            this(id.value(), name);
        }
    }
}
