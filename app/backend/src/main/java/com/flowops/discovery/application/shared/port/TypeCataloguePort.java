package com.flowops.discovery.application.shared.port;

import com.flowops.discovery.domain.enums.TrackTypeStatus;
import com.flowops.discovery.domain.model.TrackType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TypeCataloguePort {
    void save(TrackType type);

    Optional<TrackType> findType(UUID id);

    List<TrackType> typesByStatus(TrackTypeStatus... statuses);

    Optional<TrackType> findByName(String name);
}
