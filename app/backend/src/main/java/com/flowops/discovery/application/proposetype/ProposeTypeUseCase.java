package com.flowops.discovery.application.proposetype;

import com.flowops.discovery.domain.enums.OutputType;
import com.flowops.discovery.domain.enums.TrackTypeStatus;
import java.util.List;
import java.util.UUID;

public interface ProposeTypeUseCase {
    List<DiscoveredType> execute();

    record DiscoveredType(
            UUID typeId,
            String name,
            TrackTypeStatus status,
            int occurrenceCount,
            String fromRoleName,
            String toRoleName,
            OutputType terminalOutputType) {}
}
